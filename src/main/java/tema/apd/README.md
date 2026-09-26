Descrierea Temei 1 - APD - Bănescu Ema-Ioana

Sectiunea 1:

Personal, tema mi s-a parut extrem de interesanta, ca si tematica, dar si datorita
faptului ca a presupus implementarea in Java a unei aplicatii multi-threading,
foarte utila in practica.

La inceput, cred ca partea de setup cu Maven, alcatuirea fisierului pom.xml,
apoi schimbarea lui pentru a putea rula proiectul si pe checkerul de pe moodle, mi s-a parut putin
dificil de facut, pentru ca a trebuit sa rezolv destul de multe erori, unele fisiere nu erau vazute de
catre fisierele sursa create in main/java/tema/apd atunci cand se excuta Makefile-ul. De asemenea, ceva
care mie una mi s-a parut putin confusing, a fost metoda de testare, adica la o rulare locala lucrurile
pareau corecte si scalabile, adica in parametrii, insa checkerul cateodata imi dadea maxim-ul de puncte, dar
alteori imi picau 1 sau 2 teste de scalabilitate, avand eroarea ca acceleratia medie ar fi prea mica.
Eu in cadrul implementarii, am incercat sa gestionez posibilele edge case-uri si sa ma gandesc daca ar aparea
coliziuni sau diverse probleme de concurenta, insa aici problema era acea acceleratie sub parametrul acceptat
Am incercat sa optimizez, sa fac niste hashMap-uri locale (dezvolt in sectiunea 2) thradurilor, care apoi
sa propage rezultatele in structurile globale din Tema1.

Ca si tematica, mi-a placut faptul ca a fost ceva practic, ceva care poate fi mai apoi dezvoltat, poate
chiar integrat intr-o arhitectura mult mai complexa, cu parte de front-end, baza de date, si de asemenea mi-a placut
ca a presupus interactiunea cu fisierele json in java, lucru care nu a fost atins in cadrul materiei Programare
Orientata Obiect.


Sectiunea 2: Strategia de paralelizare:

2.1 Modul in care am impartit munca dintre threaduri

Abordarea a fost una clasica, am avut initial intentia de a folosi interfata ExecutorService, insa m-am gandit
ca totusi o abordare simpla cu threaduri si cu impartirea pe etape pt fiecare thread fiind alocat un numar de operatiuni
de la idx_start -> idx_end ar fi mai facila, intrucat in general ai mai mult control asupra prioritatilor, a granularitatii
si a intregului ciclu de viata. 

Avantajele thread polling-ul ar fi fost reutilizarea threadurilor (interzisa prin cerinta), bun pentru taskuri
de scurta durata si cand am multe taskuri independente. Insa la mine taskurile depind de niste faze sincrone, am folosit bariere
pentru acest tip de sincronizare si de divizare a computatiilor pe cateva etape.

Asa ca am mers pe varianta clasica in care fiecare thread proceseaza un interval static  de date.
(prin formulele deduse si la lab: start = id * N / P si end = (id + 1) * N / P. Deci pentru un thread,
a trebuit sa retin ca atribut (field) id-ul lui unic, ce portiune din lista de articole sa parcurga atunci cand voiam sa elimin duplicatele
si ce structuri locale (preponderent hashMap-uri) sa populeze.

Etapa 1:
Citirea fisierelor JSON.
La nivel de thread, fiecare preia numele urmatorului fisier de interes, prin 
"index = filesContor.getAndIncrement()". M-am gandit sa nu impart citirea fisierelor in mod static, pentru ca poate nu pe exemplele
din cadrul testelor automate, dar in general as fi putut avea fisiere cu foarte putine articole, unele cu foarte multe articole.
Astfel, mi se pare ca abordarea mea distribuie totul mai echilibrat si gestioneaza mai bine situatiile
in care un thread termina treaba mult prea devreme.

La finalul acestei etape, am toate articolele de tipul (Article) stocate intr-o lista Collections.synchronizedList

Etapa 2: - Partea de eliminare a duplicatelor -
2.1

Dupa ce mi-am stocat articolele in lista si am asteptat la bariera, impart intervalul
0 - size_allArticlesList in range-uri pentru fiecare thread.
As fi putut foarte simplu sa folosesc, pentru identificarea elementelor duplicate, doua ConcurrentHashMap-uri
globale (uuidFreq si titlesFreq) si sa fac actualizari concurente prin merge(). Aceasta chiar era
abordarea mea initiala. Desi corecta (si cu scor mai mare pe local..), m-am gandit ca ar produce foarte
mult lock contention pentru ca toate thread-urile se bat pe aceleasi chei.

Astfel ca, fiecare thread isi creeaza 2 hashMap-uri locale, localUuidFreq si localTitleFreq
Introduc o bariera, apoi dupa bariera fiecare thread va faca merge in structurile globale.

Apoi iar astept la bariera, intrucat vreau ca toate sa fie introduse in Collection.synchronizedList-ul de articole
unice, iar apoi pot trece la pasul 2.2

2.2
Acum, fiecare thread va prelucra doar articolele unice din propriul lor interval. Astfel ca
pentru fiecare articol, am creat si am updatat map-urile locale, care memoreaza informatiile urmatoare:

localCatMap -> maparea categorie -> lista de uuids
localLangMap -> maparea limba -> lista de uuids
localCategoryCount -> maparea categorie -> numar de articole cu acea categorie
localLanguageCount -> maparea limba -> nr de articole unice scrise in acea limba.

Am folosit hashMap-uri normale, intrucat sunt accesate in mod exclusiv de catre threadul curent
si nu ar fi existat vreun risc ca 2 sau mai multe threaduri sa scrie in aceiasi structura.

Dupa finalizarea pasului de mai sus, am facut merge in structurile globale:
mapCatIndices, mapLimbiIndices, mapCatCounts, mapLangCounts. ( aici am folosit functia `merge`).

Per total, in defavoarea abordarii cu o singura zona globala partajata si atat (fara structuri locale),
solutia de mai sus minimizeaza lock contention-ul, reduce iterarea prin  structuri globale si creste scalabilitatea,
lucru observat prin testare atat manuala cat si automata.


Etapa 3: - Identificarea celui mai recent articol

3.1
Aici, thread-urile parcurg articolele unice din range-ul aferent fiecaruia,
mentinand o variabila localRecentArticle ( comparatii bazate pe published, apoi lexicografic,
dupa uuid). Apoi, fiecare thread va actualiza variabila globala mostRecentArticle, dar am asigurat
corectitudinea, introducand asta in blocul `synchronized` pe lockul Tema1.mostRecentArticleLock

3.2
Mai devreme la o etapa anterioara, populasem structurile locale -> acum fiecare thread le va da merge
in hashMap-urile globale.

3.3 - filtrarea cuvintelor de interes
Fiecare thread se va uita in range-ul sau, in uniqueArticles, va face prelucrarile
pe text, din cerinta, aici am folosit si un Set local ca sa evit contorizarea in mod repetat
a aceluiasi cuvant. Apoi am actualizat global hashMap-ul keyWordCount prin merge().

4 Generarea rezultatelor finale
Aici, threadul cu id-ul 0 (am ales eu sa il desemnez pe el), dupa ULTIMA bariera,
acest thread va fi cel care se va ocupa de generarea raportului final (statisticilor)
A se vedea comentarii (aici nu intru prea mult in detalii, intrucat nu am avut mari mecanisme de sincronizare aici).
Ci doar sunt niste loook-upuri prin structurile globale din Tema1 (ex): keyWordCount) si extragerea
in cazul (ex) a cuvantului cu cel mai mare nr de articole in care il gasesc.


