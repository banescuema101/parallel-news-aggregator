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
 * Comparator - compararea a doua articole lexicografic pe sirurile published, apoi
 * in caz de egalitate, pe uuid (cum este specificat si in enunt)
 */
class MyComparator implements Comparator<Article> {
	@Override
	public int compare(Article art1, Article art2) {
		int rez = art2.getPublished().compareTo(art1.getPublished());
		if (rez != 0) {
			return rez;
		}
		// egalitate - sortare crescătoare după uuid
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
			// aici ma uit lexicografic, dupa cheie:
			return t1.getKey().compareTo(t2.getKey());
		}
	}
}


public class Tema1 {
	// variabile GLOBALE tuturor threadurilor, unde ele colaborativ, vor pune rezultatele locale ce maximizeaza
	// rezultatul global, apoi threadul cu id 0 va face merge in aceste variabile globale.
	public static String nameBestAuthor;
	public static String topLanguage;
	public static String topCategory; // categoria TOP, cea mai populara
	static String topKeyWord;
	public static int bestAuthorCount;
	public static int topLanguageCount;
	public static int topCategoryCount; // nr de articole aferent categoriei TOP, cea mai populara
	public static int topKeyWordCount;

	// mapari GLOBALE categorie -> nr Articole cu acea categorie
	static ConcurrentHashMap<String, Integer> mapCatCounts = new ConcurrentHashMap<>();
	static ConcurrentHashMap<String, Integer> mapLangCounts = new ConcurrentHashMap<>();

	// pentru eliminarea duplicatelor:
	static List<Article> allArticles = Collections.synchronizedList(new ArrayList<>());
	static List<Article> uniqueArticles = Collections.synchronizedList(new ArrayList<>());

	// fiecare thread are si el un uuidFreq si titlesFreq local, si dupa ce termina in range-ul aferent, local,
	// de calculat, va face o operatie de .merge() pe aceste doua ConcurrentHashMaps. Din cate am aflat,
	// merge este o operatie care are loc in mod atomic, cu niste mecanisme interne de locking la nivel de bucket.
	// (ar fi fost o problema daca 2 sau mai multe threaduri ar fi incercat sa scrie in acelasi bucket in acelasi timp..
	// de asta am optat pentru ConcurrentHashMap
	static ConcurrentHashMap<String, Integer> uuidFreq = new ConcurrentHashMap<>();
	static ConcurrentHashMap<String, Integer> titlesFreq = new ConcurrentHashMap<>();


	static List<String> jsonFilesStrings;
	// pentru rapoarte:
	// autor -> nr Articole scrise
	static ConcurrentHashMap<String, Integer> mapAuthorNr = new ConcurrentHashMap<>();
	// unde tin cuvintele de interes -> si frecventa de aparitie.
	final static ConcurrentHashMap<String, Integer> keywordCount = new ConcurrentHashMap<>();

	// pentru fisierele pe limbi
	static ConcurrentHashMap<String, List<String>> mapLimbiIndices = new ConcurrentHashMap<>();
	// pentru fisierele pe categorii.
	static ConcurrentHashMap<String, List<String>> mapCatIndices = new ConcurrentHashMap<>();

	// variabila locala unde voi retine CEL MAI RECENT ARTICOL.
	static Article mostRecentArticle = null;
	final static Object mostRecentArticleLock = new Object();

	// !! Pentru ca ma trezisem cu diferente la contorizari intre fisierele de output generate de cheker
	// vs referintele -> si mi-am dat seama ca omisesm sa verific si validitatea limbilor / categoriilor !!
	// le preiau din parsarea inputs.txt
	static Set<String> setLanguages;
	static Set<String> setCategories;
	static Set<String> setlinkingWords;

	static CyclicBarrier barrier;

	public static void main(String[] args) throws Exception {

		int nrThreads = Integer.parseInt(args[0]);

		Thread[] threads = new Thread[nrThreads];
		String articlesTxt = args[1];
		String inputsTxt = args[2];

		// imi memorez lista de String-uri
		jsonFilesStrings = parsareFileNamesArticles(articlesTxt);
		citireFisierInputs(inputsTxt);

		barrier = new CyclicBarrier(nrThreads);

		// pornesc cele nrThreads threaduri (la fiecare obiect de tipul Thread parsez id-ul i si numarul de Threaduri
		// care e P - notat in MyThread asa)
		for (int i = 0; i < nrThreads; i++) {
			threads[i] = new Thread(new MyThread(i, nrThreads));
			threads[i].start();
		}

		// la final fac join, adica le contopesc inapoi in threadul Main principal, apoi inchei si programul Main.
		// Threadul cu id-ul 0 va afisa statisticile, apeland metoda @scriereRezultate din Main
		for (int i = 0; i < nrThreads; i++) {
			try {
				threads[i].join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	// citirea efectiva a json-urilor se intampla in metoda run() a fiecarui thread. Aici doar imi fac lista
	// cu toate denumirile din fisierul articles.txt, ca dupa sa le distribui cu range-uri fiecarui thread,
	// si ele sa preia efectiv json-urile.

	static List<String> parsareFileNamesArticles(String path) throws Exception {
		// lista de nume de fisiere din articles.txt.
		List<String> files = new ArrayList<>();
		File fileArticles = new File(path);
		String parentDir = fileArticles.getParent();

		BufferedReader br = new BufferedReader(new FileReader(path));
		// pe prima linie am numarul de fisiere.
		String line = br.readLine();
		int n = Integer.parseInt(line.trim());

		// aici dadea eroare altfel. Am fost nevoita sa ma raportez la parintele fisierului si
		// la calea relativa, nu doar la cea relativa.
		for (int i = 0; i < n; i++) {
			String relativePath = br.readLine();

			File realPath;
			if (parentDir == null) {
				// daca aici sunt cu sursele, inseamna ca nu e relativ la alt director, in afara de acesta.
				realPath = new File(relativePath);
			} else {
				// in acest caz fisierul are o succesiune de posibile folderuri. (care il precede)
				realPath = new File(parentDir, relativePath);
			}
			files.add(realPath.getPath());
		}
		br.close();
		return files;
	}


	// chiar daca stiu ca limbile/categoriile din acele fisiere din inputs.txt
	// sunt unice (din exemple), o sa le memorez in seturi, pentru ca nu ma intereseaza ordinea.
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


	// aici ma ocup sa pun in seturile de categorii, labnguages si linkingWords, continutul fisierelor
	// din cadrul inputs.txt
	// adica am citit ce contine inputs.txt:
	// ../../files/languages.txt
	//../../files/categories.txt
	//../../files/english_linking_words.txt

	// Le-am deschis pe fiecare, si apoi am citit rand cu rand, adaugand in seturi.
	// Atunci cand voi cauta, look-up urile vor fi mult mai rapide in O(1), nu ma intereseaza ordinea,
	// ma ajuta doar in a verifica daca o categorie este VALIDA sau o ignor, in MyThreads (similar si setul pt limbi
	// si pentru linking words.
	static void citireFisierInputs(String inputsFileTxt) throws Exception{
		File fileInput = new File(inputsFileTxt);
		String parentDir = fileInput.getParent();
		BufferedReader br = new BufferedReader(new FileReader(fileInput));

		br.readLine(); // consum numarul de pe prima linie
		String pathLangFile = br.readLine().trim();
		String pathCatFile = br.readLine().trim();
		String pathWordsFile = br.readLine().trim();

		// si creez calea corecta -> parinte + calea relativa
		File completeLangFilePath;
		File completeCatFilePath;
		File completeWordsFilePath;
		// acelasi lucru, crearea caii absolute.
		if (parentDir != null) {
			completeLangFilePath = new File(parentDir + File.separator + pathLangFile);
			completeCatFilePath = new File(parentDir + File.separator + pathCatFile);
			completeWordsFilePath = new File(parentDir + File.separator + pathWordsFile);
		} else {
			completeLangFilePath = new File(pathLangFile);
			completeCatFilePath = new File(pathCatFile);
			completeWordsFilePath = new File(pathWordsFile);
		}

		// seturile
		setLanguages = creeazaSet(completeLangFilePath.getPath());
		setCategories = creeazaSet(completeCatFilePath.getPath());
		setlinkingWords = creeazaSet(completeWordsFilePath.getPath());

		Set<String> cleanedWords = ConcurrentHashMap.newKeySet();
		for (String w : setlinkingWords) {
			// le transform in litere mici, apoi elimin ceea ce nu este litera!
			w = w.toLowerCase().replaceAll("[^a-z]", "");
			cleanedWords.add(w);
		}
		setlinkingWords = cleanedWords;
		br.close();
	}

	static void scriereRezultate() throws Exception {
		// articolele mele !unice!
		uniqueArticles.sort(new tema.apd.MyComparator());
		// le-am sortat cu comparatorul care imi compara dupa published 2 articole, iar apoi in caz de egalitate la timestamp
		// dupa uuid lexicografic.
		PrintWriter pw = new PrintWriter("all_articles.txt");

		for (Article art : uniqueArticles) {
			pw.println(art.getUuid() + " " + art.getPublished());
		}
		pw.close();

		Set<Map.Entry<String, List<String>>> entrySet = mapCatIndices.entrySet();
		for (Map.Entry<String, List<String>> entry : entrySet) {
			String category = entry.getKey();
			List<String> listUuids = entry.getValue();

			category = category.trim();
			category = category.replaceAll(",", "");
			category = category.replaceAll(" ", "_");

			PrintWriter pw2 = new PrintWriter(category + ".txt");

			Collections.sort(listUuids); // implicit sortarea aici va fi lexicografica.
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

				Collections.sort(listLangUuids); // lexicografic uuid-urile
				for (String listLangUuid : listLangUuids) {
					pw3.println(listLangUuid);
				}
				pw3.close();
			}
		}

		PrintWriter pw4 = new PrintWriter("keywords_count.txt");
		List<Map.Entry<String, Integer>> listKeyWordsFreq = new ArrayList<>(keywordCount.entrySet());
		// prima data vreau sa compar in functie de valoare (nr de aparitii al acelui keyword, intai
		// cele cu acest numar mai mare, apoi in caz de egalitate,
		// voi lua lexicografic care keyword e mai "mic" lexicografic, va intra primul in lista sortata.
		listKeyWordsFreq.sort(new tema.apd.MyComparatorKewords());

		for (Map.Entry<String, Integer> entry : listKeyWordsFreq) {
			pw4.println(entry.getKey() + " " + entry.getValue());
		}
		pw4.close();


		// pentru rapoarte
		// count unicate/ duplicate, cel mai tare autor, limba de top etc etc
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
