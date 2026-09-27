package tema.apd;
import java.io.*;
import java.util.Map;
import java.util.List;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Set;


import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;


/**
 * Comparator - compares two articles lexicographically by published date strings,
 * and in case of equality, by uuid (as specified in the requirements)
 */
class MyComparator implements Comparator<Article> {
	@Override
	public int compare(Article art1, Article art2) {
		int rez = art2.getPublished().compareTo(art1.getPublished());
		if (rez != 0) {
			return rez;
		}
		// tie-breaker - ascending lexicographical sort by uuid
		return art1.getUuid().compareTo(art2.getUuid());
	}
}

class MyComparatorKewords implements Comparator<Map.Entry<String, Integer>> {
	@Override
	public int compare(Map.Entry<String, Integer> t1, Map.Entry<String, Integer> t2) {
		int cmpVal = t1.getValue().compareTo(t2.getValue());
		if (cmpVal > 0) {
			return -1;
		} else if (cmpVal < 0) {
			return 1;
		} else {
			// tie-breaker: sort lexicographically by key
			return t1.getKey().compareTo(t2.getKey());
		}
	}
}


public class Tema1 {
	// Global variables across all worker threads where local results that maximize
	// global metrics are merged - thread 0 merges and finalizes them.
	public static String nameBestAuthor;
	public static String topLanguage;
	public static String topCategory; // The most popular (TOP) category
	static String topKeyWord;
	public static int bestAuthorCount;
	public static int topLanguageCount;
	public static int topCategoryCount; // number of articles matching the TOP category
	public static int topKeyWordCount;

	// Global mappings: Category -> Article count for that category
	static ConcurrentHashMap<String, Integer> mapCatCounts = new ConcurrentHashMap<>();
	static ConcurrentHashMap<String, Integer> mapLangCounts = new ConcurrentHashMap<>();

	// Article collections used for duplicate removal.
	static List<Article> allArticles = Collections.synchronizedList(new ArrayList<>());
	static List<Article> uniqueArticles = Collections.synchronizedList(new ArrayList<>());

	// Each thread tracks local uuidFreq and titlesFreq, and merges them into
	// these ConcurrentHashMaps via .merge() once their assigned range finishes.
	// The merge operation executes atomically using bucket-level locks to prevent data races.
	static ConcurrentHashMap<String, Integer> uuidFreq = new ConcurrentHashMap<>();
	static ConcurrentHashMap<String, Integer> titlesFreq = new ConcurrentHashMap<>();


	static List<String> jsonFilesStrings;
	// Aggregation maps: Author -> Article count
	static ConcurrentHashMap<String, Integer> mapAuthorNr = new ConcurrentHashMap<>();
	// Inverted index for keywords of interest -> distinct article occurrence frequency
	final static ConcurrentHashMap<String, Integer> keywordCount = new ConcurrentHashMap<>();

	// Article mappings: Language -> List of Article UUIDs
	static ConcurrentHashMap<String, List<String>> mapLimbiIndices = new ConcurrentHashMap<>();
	// Article mappings: Category -> List of Article UUIDs
	static ConcurrentHashMap<String, List<String>> mapCatIndices = new ConcurrentHashMap<>();

	// Shared reference to track the most recently published article
	static Article mostRecentArticle = null;
	final static Object mostRecentArticleLock = new Object();

	// Sets used to validate languages, categories and linking words loaded from inputs.txt
	static Set<String> setLanguages;
	static Set<String> setCategories;
	static Set<String> setlinkingWords;

	static CyclicBarrier barrier;

	public static void main(String[] args) throws Exception {

		int nrThreads = Integer.parseInt(args[0]);

		Thread[] threads = new Thread[nrThreads];
		String articlesTxt = args[1];
		String inputsTxt = args[2];

		// Parse file paths and filter lists on the main thread
		jsonFilesStrings = parsareFileNamesArticles(articlesTxt);
		citireFisierInputs(inputsTxt);

		barrier = new CyclicBarrier(nrThreads);

		// Spawn the fixed set of worker threads with their respective IDs and total count P
		for (int i = 0; i < nrThreads; i++) {
			threads[i] = new Thread(new MyThread(i, nrThreads));
			threads[i].start();
		}

		// Await thread completion, join back into the main thread, and finish execution
		// Thread 0 writes the final aggregated reports via scriereRezultate()
		for (int i = 0; i < nrThreads; i++) {
			try {
				threads[i].join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	// Article JSON parsing takes place inside each thread's run() method
	// Here we collect all article file paths from articles.txt to distribute ranges to each thread

	static List<String> parsareFileNamesArticles(String path) throws Exception {
		// List of article filenames from articles.txt
		List<String> files = new ArrayList<>();
		File fileArticles = new File(path);
		String parentDir = fileArticles.getParent();

		BufferedReader br = new BufferedReader(new FileReader(path));
		// The first line contains the total number of files
		String line = br.readLine();
		int n = Integer.parseInt(line.trim());

		// Resolve paths relative to parent directory if applicable
		for (int i = 0; i < n; i++) {
			String relativePath = br.readLine();

			File realPath;
			if (parentDir == null) {
				// In current directory; path is already direct
				realPath = new File(relativePath);
			} else {
				// File path is preceded by parent directory structure
				realPath = new File(parentDir, relativePath);
			}
			files.add(realPath.getPath());
		}
		br.close();
		return files;
	}


	// Loads elements into a concurrent set to achieve O(1) membership lookups,
	// ignoring ordering.
	static Set<String> creeazaSet(String file) throws Exception{
		BufferedReader br = new BufferedReader(new FileReader(file));
		int n = Integer.parseInt(br.readLine().trim());

		Set<String> set = ConcurrentHashMap.newKeySet();
		for (int i = 0; i < n; i++) {
			set.add(br.readLine().trim());
		}
		br.close();
		return set;
	}


	// Parses categories, languages, and linking words paths listed inside inputs.txt:
	// e.g.:
	// ../../files/languages.txt
	// ../../files/categories.txt
	// ../../files/english_linking_words.txt
	//
	// Each target file is read line-by-line and stored into sets.
	// Provides O(1) lookups in worker threads to validate categories, languages, and linking words
	static void citireFisierInputs(String inputsFileTxt) throws Exception{
		File fileInput = new File(inputsFileTxt);
		String parentDir = fileInput.getParent();
		BufferedReader br = new BufferedReader(new FileReader(fileInput));

		br.readLine(); // Consume item count header
		String pathLangFile = br.readLine().trim();
		String pathCatFile = br.readLine().trim();
		String pathWordsFile = br.readLine().trim();

		// Resolve absolute/complete paths using parent directory
		File completeLangFilePath;
		File completeCatFilePath;
		File completeWordsFilePath;

		if (parentDir != null) {
			completeLangFilePath = new File(parentDir + File.separator + pathLangFile);
			completeCatFilePath = new File(parentDir + File.separator + pathCatFile);
			completeWordsFilePath = new File(parentDir + File.separator + pathWordsFile);
		} else {
			completeLangFilePath = new File(pathLangFile);
			completeCatFilePath = new File(pathCatFile);
			completeWordsFilePath = new File(pathWordsFile);
		}

		// Populate lookup sets
		setLanguages = creeazaSet(completeLangFilePath.getPath());
		setCategories = creeazaSet(completeCatFilePath.getPath());
		setlinkingWords = creeazaSet(completeWordsFilePath.getPath());

		Set<String> cleanedWords = ConcurrentHashMap.newKeySet();
		for (String w : setlinkingWords) {
			// Normalize to lowercase and strip all non-letter characters
			w = w.toLowerCase().replaceAll("[^a-z]", "");
			cleanedWords.add(w);
		}
		setlinkingWords = cleanedWords;
		br.close();
	}

	static void scriereRezultate() throws Exception {
		// Sort unique articles chronologically descending, falling back to UUID lexicographical order
		uniqueArticles.sort(new tema.apd.MyComparator());
		PrintWriter pw = new PrintWriter("all_articles.txt");

		for (Article art : uniqueArticles) {
			pw.println(art.getUuid() + " " + art.getPublished());
		}
		pw.close();

		// Generate per-category output files
		Set<Map.Entry<String, List<String>>> entrySet = mapCatIndices.entrySet();
		for (Map.Entry<String, List<String>> entry : entrySet) {
			String category = entry.getKey();
			List<String> listUuids = entry.getValue();

			category = category.trim();
			category = category.replaceAll(",", "");
			category = category.replaceAll(" ", "_");

			PrintWriter pw2 = new PrintWriter(category + ".txt");

			Collections.sort(listUuids); // Default lexicographical sort by UUID
			for (String uuid : listUuids) {
				pw2.println(uuid);
			}
			pw2.close();
		}

		List<Map.Entry<String, List<String>>> listLanguageMap = new ArrayList<>(mapLimbiIndices.entrySet());
		for (Map.Entry<String, List<String>> entryLang : listLanguageMap) {
			if (setLanguages.contains(entryLang.getKey())) {
				PrintWriter pw3 = new PrintWriter(entryLang.getKey() + ".txt");
				List<String> listLangUuids = entryLang.getValue();

				Collections.sort(listLangUuids); // Generate per-language output files
				for (String listLangUuid : listLangUuids) {
					pw3.println(listLangUuid);
				}
				pw3.close();
			}
		}

		PrintWriter pw4 = new PrintWriter("keywords_count.txt");
		List<Map.Entry<String, Integer>> listKeyWordsFreq = new ArrayList<>(keywordCount.entrySet());
		// Generate keywords_count.txt: sorted by frequency descending, then lexicographically
		listKeyWordsFreq.sort(new tema.apd.MyComparatorKewords());

		for (Map.Entry<String, Integer> entry : listKeyWordsFreq) {
			pw4.println(entry.getKey() + " " + entry.getValue());
		}
		pw4.close();

		// Output reports.txt with global aggregated metrics
		PrintWriter pw5 = new PrintWriter("reports.txt");
		pw5.println("duplicates_found - " + (allArticles.size() - uniqueArticles.size()));
		pw5.println("unique_articles - " + uniqueArticles.size());
		pw5.println("best_author - " + nameBestAuthor + " " + bestAuthorCount);
		pw5.println("top_language - " + topLanguage + " " + topLanguageCount);
		pw5.println("top_category - " + topCategory + " " + topCategoryCount);
		pw5.println("most_recent_article - " + mostRecentArticle.toString());
		pw5.println("top_keyword_en - " + topKeyWord + " " + topKeyWordCount);
		pw5.close();
	}
}
