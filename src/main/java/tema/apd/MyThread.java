package tema.apd;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Vector;

public class MyThread implements Runnable {
	private int id;
	private int P;
	// thread-local data structures to avoid lock contention
	private Map<String, List<String>> localCatMap;
	private Map<String, List<String>> localLangMap;
	private Map<String, Integer> localLanguageCount;
	private Map<String, Integer> localCategoryCount;
	private ObjectMapper mapper;

	private Map<String,Integer> localUuidFreq;
	private Map<String,Integer> localTitleFreq;

	// Constructor
	public MyThread(int id, int nrThreads) {
		this.id = id;
		this.P = nrThreads;
		this.localLanguageCount = new HashMap<>();
		this.localCategoryCount = new HashMap<>();
		this.localCatMap = new HashMap<>();
		this.localLangMap = new HashMap<>();
		this.localUuidFreq = new HashMap<>();
		this.localTitleFreq = new HashMap<>();

		// configure jackson object mapper to safely ignore unspecified attributes.
		mapper = new ObjectMapper();
		mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public int getP() {
		return P;
	}

	public void setP(int p) {
		P = p;
	}

	public Map<String, List<String>> getLocalCatMap() {
		return localCatMap;
	}

	public void setLocalCatMap(Map<String, List<String>> localCatMap) {
		this.localCatMap = localCatMap;
	}

	public Map<String, List<String>> getLocalLangMap() {
		return localLangMap;
	}

	public void setLocalLangMap(Map<String, List<String>> localLangMap) {
		this.localLangMap = localLangMap;
	}

	public Map<String, Integer> getLocalLanguageCount() {
		return localLanguageCount;
	}

	public void setLocalLanguageCount(Map<String, Integer> localLanguageCount) {
		this.localLanguageCount = localLanguageCount;
	}

	public Map<String, Integer> getLocalCategoryCount() {
		return localCategoryCount;
	}

	public void setLocalCategoryCount(Map<String, Integer> localCategoryCount) {
		this.localCategoryCount = localCategoryCount;
	}

	public ObjectMapper getMapper() {
		return mapper;
	}

	public void setMapper(ObjectMapper mapper) {
		this.mapper = mapper;
	}

	public Map<String, Integer> getLocalUuidFreq() {
		return localUuidFreq;
	}

	public void setLocalUuidFreq(Map<String, Integer> localUuidFreq) {
		this.localUuidFreq = localUuidFreq;
	}

	public Map<String, Integer> getLocalTitleFreq() {
		return localTitleFreq;
	}

	public void setLocalTitleFreq(Map<String, Integer> localTitleFreq) {
		this.localTitleFreq = localTitleFreq;
	}

	@Override
	public void run() {
		try {

			/*
			 * Work Partitioning Strategy trade-off:
			 *
			 * Considered dynamic round-robin scheduling (Thread[i % P] -> File[i]) to mitigate potential load imbalance
			 * caused by non-uniform JSON file sizes. However, with large batches of small files, cyclic allocation increases
			 * thread context switching overhead and destroys CPU cache locality.
			 *
			 * Decided on static contiguous range partitioning: each thread processes a deterministic slice [start, end)
			 * of the file list. This minimizes synchronization overhead, optimizes throughput, and scales linearly when
			 * I/O latency dominates.
			 */
			int FilesCounter = NewsAggregator.jsonFilesStrings.size();
			int startFiles = (int)(id * (double) FilesCounter / P);
			int endFiles = Math.min((id + 1) * FilesCounter / P, FilesCounter);

			for (int i = startFiles; i < endFiles; i++) {
				File fd = new File(NewsAggregator.jsonFilesStrings.get(i));
				if (fd.exists()) {
					Article[] arr = mapper.readValue(fd, Article[].class);
					for (Article art : arr) {
						NewsAggregator.allArticles.add(art);
					}
				}
			}
			// Synchronize threads: ensure all JSON articles are fully loaded into allArticles
			NewsAggregator.barrier.await();


			// Phase 2: Duplicate detection and collision counting
			int ArticlesCounter = NewsAggregator.allArticles.size();
			int start = (int)(id * (double) ArticlesCounter / P);
			int end = Math.min((id + 1) * ArticlesCounter / P, ArticlesCounter);


			for (int i = start; i < end; i++) {
				Article currArticle = NewsAggregator.allArticles.get(i);
				// Update local frequencies for UUID and Title
				localUuidFreq.put(currArticle.getUuid(), localUuidFreq.getOrDefault(currArticle.getUuid(), 0) + 1);
				localTitleFreq.put(currArticle.getTitle(), localTitleFreq.getOrDefault(currArticle.getTitle(), 0) + 1);
			}
			NewsAggregator.barrier.await();

			// Atomically merge local frequency maps into global concurrent tables
			for (Map.Entry<String, Integer> entry : localUuidFreq.entrySet()) {
				NewsAggregator.uuidFreq.merge(entry.getKey(), entry.getValue(), Integer::sum);
			}

			for (Map.Entry<String, Integer> entry : localTitleFreq.entrySet()) {
				NewsAggregator.titlesFreq.merge(entry.getKey(), entry.getValue(), Integer::sum);
			}
			NewsAggregator.barrier.await();

			// Filter out duplicate articles into Tema1.uniqueArticle
			for (int i = start; i < end; i++) {
				Article currArticle = NewsAggregator.allArticles.get(i);
				if (NewsAggregator.uuidFreq.get(currArticle.getUuid()) == 1 && NewsAggregator.titlesFreq.get(currArticle.getTitle()) == 1) {
					NewsAggregator.uniqueArticles.add(currArticle);
				}
			}

			NewsAggregator.barrier.await();

			// Phase 3: Metadata indexing and statistics tracking on unique articles
			int uniqueArticlesCount = NewsAggregator.uniqueArticles.size();
			int newStart = (int)(id * (double) uniqueArticlesCount / P);
			int newEnd = Math.min((id + 1) * uniqueArticlesCount / P, uniqueArticlesCount);


			Article localRecentArticle = null;

			for (int i = newStart; i < newEnd; i++) {
				Article currArticle = NewsAggregator.uniqueArticles.get(i);
				List<String> artCategoriesList = currArticle.getCategories();
				if (artCategoriesList == null) {
					continue;
				}
				// Deduplicate categories within the same article to avoid duplicate counts
				Set<String> categories = new HashSet<>(artCategoriesList);
				for (String category : categories) {
					if (NewsAggregator.setCategories.contains(category) && category != null && currArticle.getUuid() != null)
					{
						localCatMap.putIfAbsent(category, new ArrayList<>());
						localCatMap.get(category).add(currArticle.getUuid());

						localCategoryCount.put(category, localCategoryCount.getOrDefault(category, 0) + 1);
					}
				}

				// Map valid languages and increment article occurrence
				String artLanguage = currArticle.getLanguage();
				if (artLanguage != null && NewsAggregator.setLanguages.contains(artLanguage)) {
					localLangMap.putIfAbsent(artLanguage, new ArrayList<>());
					localLangMap.get(artLanguage).add(currArticle.getUuid());

					localLanguageCount.put(artLanguage, localLanguageCount.getOrDefault(artLanguage, 0) + 1);
				}
				// Track author publication counts globally
				String author = currArticle.getAuthor();
				NewsAggregator.mapAuthorNr.merge(author, 1, (a, b) -> a + 1);


				// Track the thread-local most recent article
				if (localRecentArticle == null) {
					localRecentArticle = currArticle;
				} else {
					// Compare published timestamps lexicographically; break ties using UUID
					int cmpVal = currArticle.getPublished().compareTo(localRecentArticle.getPublished());
					if (cmpVal > 0) {
						localRecentArticle = currArticle;
					} else if (cmpVal == 0) {
						if ((currArticle.getUuid() != null) && (localRecentArticle.getUuid() != null)
								&& currArticle.getUuid().compareTo(localRecentArticle.getUuid()) < 0) {
							localRecentArticle = currArticle;
						}
					}
				}
			}

			if (localRecentArticle != null) {
				// Synchronize and update the globally tracked most recent article
				synchronized (NewsAggregator.mostRecentArticleLock) {
					if (NewsAggregator.mostRecentArticle == null) {
						NewsAggregator.mostRecentArticle = localRecentArticle;
					} else {
						int cmpVal = localRecentArticle.getPublished().compareTo(NewsAggregator.mostRecentArticle.getPublished());
						if (cmpVal > 0) {
							NewsAggregator.mostRecentArticle = localRecentArticle;
						} else if (cmpVal == 0) {
							// On timestamp ties, prioritize smaller UUID lexicographically
							if (localRecentArticle.getUuid().compareTo(NewsAggregator.mostRecentArticle.getUuid()) < 0) {
								NewsAggregator.mostRecentArticle = localRecentArticle;
							}
						}
					}
				}
			}

			// Merge local category mappings: Category -> List of UUIDs
			for (Map.Entry<String, List<String>> entry : localCatMap.entrySet()) {
				NewsAggregator.mapCatIndices.computeIfAbsent(entry.getKey(), k -> new Vector<>()).addAll(entry.getValue());
			}

			// Merge local language mappings: Language -> List of UUIDs
			for (Map.Entry<String, List<String>> entry : localLangMap.entrySet()) {
				NewsAggregator.mapLimbiIndices.computeIfAbsent(entry.getKey(), k -> new Vector<>()).addAll(entry.getValue());
			}

			// Populate global category counts
			for (Map.Entry<String, Integer> entry : localCategoryCount.entrySet()) {
				NewsAggregator.mapCatCounts.merge(entry.getKey(), entry.getValue(), Integer::sum);
			}

			// Populate global language counts
			for (Map.Entry<String, Integer> entry : localLanguageCount.entrySet()) {
				NewsAggregator.mapLangCounts.merge(entry.getKey(), entry.getValue(), Integer::sum);
			}



			// Phase 4: English keyword frequency profiling
			for (int i = newStart; i < newEnd; i++) {
				Article currArticle = NewsAggregator.uniqueArticles.get(i);
				if (!"english".equals(currArticle.getLanguage())) {
					continue;
				}
				if (currArticle.getText() == null) {
					continue;
				}
				String lowerText = currArticle.getText().toLowerCase();
				String[] textParts = lowerText.split("\\s+");

				// Track words locally per article to count distinct occurrences once
				Set<String> setKeyWords = new HashSet<>();
				for (String word : textParts) {
					// Strip all non-letter characters
					String wordNonLetRemoval = word.replaceAll("[^a-z]", "");
					if (wordNonLetRemoval.isEmpty())
						continue;

					// Ignore linking words
					if (NewsAggregator.setlinkingWords.contains(wordNonLetRemoval)) {
						continue;
					}
					if (setKeyWords.contains(wordNonLetRemoval)) {
						continue;
					}

					// Add to processed set and increment distinct article frequency
					setKeyWords.add(wordNonLetRemoval);
					NewsAggregator.keywordCount.merge(wordNonLetRemoval, 1, (a, b) -> a + 1);

				}
			}
			NewsAggregator.barrier.await();


			// Phase 5: Thread 0 computes aggregate statistics and writes output files
			if (id == 0) {
				// 1) Compute best author (most articles written; alphabetical tie-breaker
				String nameBestAuthor = "";
				int maxNrAuthArticles = -1;
				Set<Map.Entry<String, Integer>> bestAuthorSet = NewsAggregator.mapAuthorNr.entrySet();
				for (Map.Entry<String, Integer> entry : bestAuthorSet) {
					String currAuthor = entry.getKey();
					int nrArticlesWritten = entry.getValue();
					if (nrArticlesWritten > maxNrAuthArticles) {
						maxNrAuthArticles = nrArticlesWritten;
						nameBestAuthor = currAuthor;
					} else if (nrArticlesWritten == maxNrAuthArticles) {
						if (nameBestAuthor == null || currAuthor.compareTo(nameBestAuthor) < 0) {
							nameBestAuthor = currAuthor;

						}
					}
				}
				NewsAggregator.nameBestAuthor = nameBestAuthor;
				NewsAggregator.bestAuthorCount = maxNrAuthArticles;

				// 2) Compute top language
				String topLanguage = "";
				int maxLangCounter = -1;

				for (Map.Entry<String, Integer> entry : NewsAggregator.mapLangCounts.entrySet()) {
					String currLang = entry.getKey();
					int ArticlesLangCounter = entry.getValue();
					if (ArticlesLangCounter > maxLangCounter) {
						maxLangCounter = ArticlesLangCounter;
						topLanguage = currLang;
					} else if (ArticlesLangCounter == maxLangCounter) {
						if (topLanguage == null || currLang.compareTo(topLanguage) < 0) {
							topLanguage = currLang;
						}
					}
				}
				NewsAggregator.topLanguage = topLanguage;
				NewsAggregator.topLanguageCount = maxLangCounter;


				// 3) Compute top category with character normalization
				String topCategory = "";
				int maxCategories = -1;
				for (Map.Entry<String, Integer> entry : NewsAggregator.mapCatCounts.entrySet()) {
					String currCat = entry.getKey();
					int nrArticlesCat = entry.getValue();

					if (nrArticlesCat > maxCategories) {
						maxCategories = nrArticlesCat;
						topCategory = currCat;
					} else if (nrArticlesCat == maxCategories) {
						if (topCategory == null || currCat.compareTo(topCategory) < 0) {
							topCategory = currCat;
						}
					}
				}
				// Normalize category name: strip commas and replace spaces with underscores
				String topCategoryNormalized = topCategory.replaceAll(",", "");
				topCategoryNormalized = topCategoryNormalized.replaceAll(" ", "_");
				NewsAggregator.topCategory = topCategoryNormalized;
				NewsAggregator.topCategoryCount = maxCategories;

				// 4) Compute top English keyword (highest frequency; lexicographical tie-breaker)
				String topKeyWord = "";
				int maxKeyWordNr = -1;
				for (Map.Entry<String, Integer> entry : NewsAggregator.keywordCount.entrySet()) {
					String wordKey = entry.getKey();
					int freqArt = entry.getValue();
					if (freqArt > maxKeyWordNr) {
						topKeyWord = wordKey;
						maxKeyWordNr = freqArt;
					} else if (freqArt == maxKeyWordNr) {
						if (wordKey.compareTo(topKeyWord) < 0) {
							topKeyWord = wordKey;
						}
					}
				}
				NewsAggregator.topKeyWord = topKeyWord;
				NewsAggregator.topKeyWordCount = maxKeyWordNr;

				NewsAggregator.writeResult();
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
