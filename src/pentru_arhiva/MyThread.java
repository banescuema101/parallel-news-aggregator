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
	// structurile locale fiecarui thread
	private Map<String, List<String>> localCatMap;
	private Map<String, List<String>> localLangMap;
	private Map<String, Integer> localLanguageCount;
	private Map<String, Integer> localCategoryCount;
	private ObjectMapper mapper;

	private Map<String,Integer> localUuidFreq;
	private Map<String,Integer> localTitleFreq;
	// constructor
	public MyThread(int id, int nrThreads) {
		this.id = id;
		this.P = nrThreads;
		this.localLanguageCount = new HashMap<>();
		this.localCategoryCount = new HashMap<>();
		this.localCatMap = new HashMap<>();
		this.localLangMap = new HashMap<>();
		this.localUuidFreq = new HashMap<>();
		this.localTitleFreq = new HashMap<>();

		// pentru jackson configures
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

			// Varianta 1
			// Ma gandisem ca daca am fisiere de dimeniune variata, ar fi mai bine sa fie procesate fisierele
			// cate unul de fiecare thread: th 0 -> file-ul 0, th 1 -> files-ul 1, th 2 -> file-ul 2 s.a.s.m.d
			// apoi ciclic, th[x%nrThreaduri] = file(x), unde x de la 0 -> size ul listei de fisiere.
			// dar m-am gandit si la faptul ca ar cauza mult context switching, mai ales daca avem un numar mic de threaduri
			// si multe multe fisiere, cu putine date in ele.

			// asa ca am optat totusi pentru varianta clasica, cu range. Th0 -> o portiune din lista de fisiere
			// si extragere de articole, th 1 alta portiune etc etc.

			// Impartirea fiecarui thread cate un range de fisiere pentru a
			// parsa articolele din ele.
			int nrFiles = Tema1.jsonFilesStrings.size();
			int startFiles = (int)(id * (double) nrFiles / P);
			int endFiles = Math.min((id + 1) * nrFiles / P, nrFiles);

			for (int i = startFiles; i < endFiles; i++) {
				File fd = new File(Tema1.jsonFilesStrings.get(i));
				// daca e totul corect si exista undeva prin ierarhia proiectului:
				if (fd.exists()) {
					Article[] arr = mapper.readValue(fd, Article[].class);
					for (Article art : arr) {
						Tema1.allArticles.add(art);
					}
				}
			}
			// astept ca toate articolele sa se introduce in allArticles, de catre toate cele
			// nrThreads threaduri.
			Tema1.barrier.await();


			// pasul 2: eliminarea duplicatelor:
			int nrArticles = Tema1.allArticles.size();
			int start = (int)(id * (double) nrArticles / P);
			int end = Math.min((id + 1) * nrArticles / P, nrArticles);


			for (int i = start; i < end; i++) {
				Article currArticle = Tema1.allArticles.get(i);
				// aici voi updata frecventele pt uuid si title in concurrent hashmap-urile mele:
				localUuidFreq.put(currArticle.getUuid(), localUuidFreq.getOrDefault(currArticle.getUuid(), 0) + 1);
				localTitleFreq.put(currArticle.getTitle(), localTitleFreq.getOrDefault(currArticle.getTitle(), 0) + 1);
			}
			Tema1.barrier.await();

			// le incorporez si in hashMap-urile aferente, globale din Tema1.
			for (Map.Entry<String, Integer> entry : localUuidFreq.entrySet()) {
				Tema1.uuidFreq.merge(entry.getKey(), entry.getValue(), Integer::sum);
			}

			for (Map.Entry<String, Integer> entry : localTitleFreq.entrySet()) {
				Tema1.titlesFreq.merge(entry.getKey(), entry.getValue(), Integer::sum);
			}
			Tema1.barrier.await();

			// acum extrag articolele unice in lista Tema1.uniqueArticles. Fiecare thread
			// parcurge o portiune din toate articolele, verifica conditia de unicitate,
			// si adauga in lista uniqueArticles, care e lista synchronizedList
			for (int i = start; i < end; i++) {
				Article currArticle = Tema1.allArticles.get(i);
				if (Tema1.uuidFreq.get(currArticle.getUuid()) == 1 && Tema1.titlesFreq.get(currArticle.getTitle()) == 1) {
					Tema1.uniqueArticles.add(currArticle);
				}
			}

			Tema1.barrier.await();

			int nrUniqueArticles = Tema1.uniqueArticles.size();
			int newStart = (int)(id * (double) nrUniqueArticles / P);
			int newEnd = Math.min((id + 1) * nrUniqueArticles / P, nrUniqueArticles);


			// Aflarea celui mai recent publicat, articol:
			Article localRecentArticle = null;

			for (int i = newStart; i < newEnd; i++) {
				Article currArticle = Tema1.uniqueArticles.get(i);
				List<String> artCategoriesList = currArticle.getCategories();
				if (artCategoriesList == null) {
					continue;
				}
				// pun lista de categori intr-un set, pentru ca am observat ca pt acelasi articol,
				// se pot repeta categoriile ex: Human Interest, de doua ori intr-un .json...
				Set<String> categories = new HashSet<>(artCategoriesList);
				for (String category : categories) {
					if (Tema1.setCategories.contains(category) && category != null && currArticle.getUuid() != null)
					{
						localCatMap.putIfAbsent(category, new ArrayList<>());
						localCatMap.get(category).add(currArticle.getUuid());

						localCategoryCount.put(category, localCategoryCount.getOrDefault(category, 0) + 1);
					}
				}

				// limba -> nrArticole, logica locala apoi merge in hashMap-ul global.
				String artLanguage = currArticle.getLanguage();
				if (artLanguage != null && Tema1.setLanguages.contains(artLanguage)) {
					localLangMap.putIfAbsent(artLanguage, new ArrayList<>());
					localLangMap.get(artLanguage).add(currArticle.getUuid());

					localLanguageCount.put(artLanguage, localLanguageCount.getOrDefault(artLanguage, 0) + 1);
				}
				// mapez si autorul, si contorizez la numarul de articole scrise de acesta.
				String author = currArticle.getAuthor();
				Tema1.mapAuthorNr.merge(author, 1, (a, b) -> a + 1);


				// pentru a retine articolul cel mai recent (local)
				if (localRecentArticle == null) {
					localRecentArticle = currArticle;
				} else {
					// comparator, dupa timestampul publicarii, lexicografic, ca sunt Stringuri
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
				// acum ca am cel mai recent articol, dar local din cele procesate de acest thread curent,
				// voi incerca sa actualizez cel mai recent articol, si la nivel global.
				synchronized (Tema1.mostRecentArticleLock) {
					if (Tema1.mostRecentArticle == null) {
						Tema1.mostRecentArticle = localRecentArticle;
					} else {
						int cmpVal = localRecentArticle.getPublished().compareTo(Tema1.mostRecentArticle.getPublished());
						if (cmpVal > 0) {
							Tema1.mostRecentArticle = localRecentArticle;
						} else if (cmpVal == 0) {
							// in caz de egalitate, ma uit si eu dupa uuid, dupa cum se specifica in cerinta, si ii dau prioritate
							// sa ia locul de mostRecentArticle cel mai mic lexicografic.
							if (localRecentArticle.getUuid().compareTo(Tema1.mostRecentArticle.getUuid()) < 0) {
								Tema1.mostRecentArticle = localRecentArticle;
							}
						}
					}
				}
			}

			// merge-uri in hashMap-urile locale. categorie -> lista de uuids
			for (Map.Entry<String, List<String>> entry : localCatMap.entrySet()) {
				Tema1.mapCatIndices.computeIfAbsent(entry.getKey(), k -> new Vector<>()).addAll(entry.getValue());
			}

			// limba -> lista de uuids
			for (Map.Entry<String, List<String>> entry : localLangMap.entrySet()) {
				Tema1.mapLimbiIndices.computeIfAbsent(entry.getKey(), k -> new Vector<>()).addAll(entry.getValue());
			}

			// populez hashMap-ul in care am mapat categoriile -> nrArticole din ele, din Tema1.
			for (Map.Entry<String, Integer> entry : localCategoryCount.entrySet()) {
				Tema1.mapCatCounts.merge(entry.getKey(), entry.getValue(), Integer::sum);
			}

			// aici voi popula structura din Tema1, mapLangCounts, cea globala.
			for (Map.Entry<String, Integer> entry : localLanguageCount.entrySet()) {
				Tema1.mapLangCounts.merge(entry.getKey(), entry.getValue(), Integer::sum);
			}



			// Pasul 4 -> Cuvinte de interes in engleza: ( aici sunt cu articole unice, de la newStart pana
			// la newEnd.
			for (int i = newStart; i < newEnd; i++) {
				Article currArticle = Tema1.uniqueArticles.get(i);
				if (!"english".equals(currArticle.getLanguage())) {
					continue;
				}
				if (currArticle.getText() == null) {
					continue;
				}
				String lowerText = currArticle.getText().toLowerCase();
				String[] textParts = lowerText.split("\\s+");

				// Vreau sa retin cuvintele gasite, intr-un set,
				// Ca sa evit contorizarea kewWord-urilor de 2 sau de mai multe ori.
				Set<String> setKeyWords = new HashSet<>();
				for (String word : textParts) {
					// elimin orice nu este litera
					String wordNonLetRemoval = word.replaceAll("[^a-z]", "");
					if (wordNonLetRemoval.isEmpty())
						continue;

					// daca e in setul de cuvinte de legatura pe care trebuie sa le ignor, le ignor.
					if (Tema1.setlinkingWords.contains(wordNonLetRemoval)) {
						continue;
					}
					if (setKeyWords.contains(wordNonLetRemoval)) {
						// inseamna ca l-am mai vazut in acest articol, si i-am crescut
						// deja frecventa de aparitie in cadrul articolelor.
						continue;
					}

					// altfel, adaug in setul keyword-urilor si ii scresc frecventa (counterul)
					setKeyWords.add(wordNonLetRemoval);
					Tema1.keywordCount.merge(wordNonLetRemoval, 1, (a, b) -> a + 1);

				}
			}
			Tema1.barrier.await();


			// am ales eu jobul cu calculul statisticilor -
			// folosindu-se de structurile globale populate de threaduri
			// dupa etapele de mai sus - sa il faca threadul cu id-ul 0.
			if (id == 0) {
				// partea de rapoarte:

				// 1) pentru cel mai bun autor:
				String nameBestAuthor = "";
				int maxNrAuthArticles = -1;
				Set<Map.Entry<String, Integer>> bestAuthorSet = Tema1.mapAuthorNr.entrySet();
				for (Map.Entry<String, Integer> entry : bestAuthorSet) {
					String currAuthor = entry.getKey();
					int nrArticlesWritten = entry.getValue();
					if (nrArticlesWritten > maxNrAuthArticles) {
						maxNrAuthArticles = nrArticlesWritten;
						nameBestAuthor = currAuthor;
					} else if (nrArticlesWritten == maxNrAuthArticles) {
						if (nameBestAuthor == null || currAuthor.compareTo(nameBestAuthor) < 0) {
							nameBestAuthor = currAuthor;
							// lexicografic dupa nume, in caz caz ca ar coincide ca nr de articole scrise.
						}
					}
				}
				Tema1.nameBestAuthor = nameBestAuthor;
				Tema1.bestAuthorCount = maxNrAuthArticles;

				// 2) pentru topLanguage;
				String topLanguage = "";
				int maxLangNr = -1;

				for (Map.Entry<String, Integer> entry : Tema1.mapLangCounts.entrySet()) {
					String currLang = entry.getKey();
					int nrArticlesLang = entry.getValue();
					if (nrArticlesLang > maxLangNr) {
						maxLangNr = nrArticlesLang;
						topLanguage = currLang;
					} else if (nrArticlesLang == maxLangNr) {
						if (topLanguage == null || currLang.compareTo(topLanguage) < 0) {
							topLanguage = currLang;
						}
					}
				}
				Tema1.topLanguage = topLanguage;
				Tema1.topLanguageCount = maxLangNr;


				// 3) categoria cu cele mai multe articole pe acea categorie.
				String topCategory = "";
				int maxCatNr = -1;
				for (Map.Entry<String, Integer> entry : Tema1.mapCatCounts.entrySet()) {
					String currCat = entry.getKey();
					int nrArticlesCat = entry.getValue();

					if (nrArticlesCat > maxCatNr) {
						maxCatNr = nrArticlesCat;
						topCategory = currCat;
					} else if (nrArticlesCat == maxCatNr) {
						if (topCategory == null || currCat.compareTo(topCategory) < 0) {
							topCategory = currCat;
						}
					}
				}
				// acele specificatii din enunt in care virgulele le elimin si spatiile le inlocuiesc cu _
				String topCategoryNormalized = topCategory.replaceAll(",", "");
				topCategoryNormalized = topCategoryNormalized.replaceAll(" ", "_");
				Tema1.topCategory = topCategoryNormalized;
				Tema1.topCategoryCount = maxCatNr;

				// 4) pentru most recent article -> am deja maximul acela global in Tema1.mostRecentArticle.
				String topKeyWord = "";
				int maxKeyWordNr = -1;
				for (Map.Entry<String, Integer> entry : Tema1.keywordCount.entrySet()) {
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
				Tema1.topKeyWord = topKeyWord;
				Tema1.topKeyWordCount = maxKeyWordNr;

				Tema1.scriereRezultate();
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
