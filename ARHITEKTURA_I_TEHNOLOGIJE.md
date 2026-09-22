# VMSimulator — arhitektura i korišćene tehnologije

Materijal za pisanje rada. Tvrdnje o modelu i ViewModel-ima proverene su u izvornom kodu
(`vmsim/src/main/java/rs/ac/bg/etf/`) na stanju radnog stabla od 21. 9. 2026, grana `main` (u radnom stablu postoje
nekomitovane izmene); ponašanje kodiranog modela dodatno je proveravano pokretanjem bez GUI-ja. Opisi izgleda i rasporeda
(poglavlja 6.3, 6.4 i 8) zasnovani su na čitanju koda i projektne dokumentacije (`CLAUDE.md`), **ne** na pokretanju
grafičkog interfejsa. Tvrdnje koje potiču samo iz `CLAUDE.md` označene su u tekstu. Na kraju dokumenta (poglavlje 13)
nalazi se spisak stvari koje treba proveriti ili ispraviti pre nego što se bilo šta od ovoga citira u radu —
uključujući greške i slabosti u simulacionoj logici koje su otkrivene prilikom pisanja i u međuvremenu ispravljene.

**Napomena o terminologiji koja se provlači kroz ceo projekat.**

| Pojam u kodu / interfejsu | Značenje |
|---|---|
| *word* (reč), `wordBits` | ofset unutar stranice; veličina stranice je `2^wordBits` adresibilnih jedinica |
| *page* (stranica), `pageBits` | broj virtuelne stranice; broj stranica po korisniku je `2^pageBits` |
| *block* (blok) u TLB-u, tablici stranica i šemama | **broj fizičkog okvira** (*frame*), ne disk blok |
| *frame* (okvir) | isto što i blok u prethodnom redu; broj okvira ima `physicalAddressBits − wordBits` bitova |
| *disk block* / `disk` polje deskriptora | 32-bitna adresa bloka na disku na kome se nalazi kopija stranice |
| *addressable unit* (adresibilna jedinica) | najmanja adresibilna jedinica memorije, 1, 2, 4 ili 8 bajtova; memorijska reč ima `addressableUnit · 8` bitova |
| *user* (korisnik) | proces/adresni prostor; svaki korisnik ima sopstvenu tablicu stranica |

---

## 1. Uvod i obim sistema

VMSimulator je desktop aplikacija edukativnog karaktera koja korak po korak prikazuje kako se jedna instrukcija
(čitanje, upis ili izvršavanje na virtuelnoj adresi) prevodi u fizičku adresu u sistemu sa **straničenjem**
(*paging*) i TLB-om. Korisnik zadaje hardversku konfiguraciju (širine adresa, veličinu TLB-a, broj korisnika, ...)
i niz instrukcija; simulator zatim izvršava instrukcije kao niz sitnih, imenovanih koraka (npr. „TLB lookup",
„Page fault", „Page evicted", „Physical address formed") koje je moguće **izvršavati unapred i poništavati unazad**.
Svaki korak se istovremeno prikazuje u tekstualnom opisu, u dnevniku izvršavanja i na šematskim prikazima
hardverskih komponenti.

Simulirane komponente (u kodu `SimulationComponent`: `MMU`, `TLB`, `OS`, `MEMORY`):

- **MMU** — formiranje adrese deskriptora u tablici stranica (pokazivač na tablicu + ofset), čitanje deskriptora,
  formiranje fizičke adrese.
- **TLB** — tri organizacije: potpuno asocijativna, direktno preslikana i skupovno asocijativna.
- **OS** — upravljanje okvirima fizičke memorije, obrada page fault-a, izbacivanje stranica (politika FIFO),
  prenos stranica sa i na disk.
- **Memorija** (i disk) — retke (sparse) reprezentacije sadržaja fizičke memorije i diska.

**Obim implementacije.** Implementirano je samo straničenje (`TranslationType.PAGED`). Enumeracija poznaje i
`SEGMENTED` i `SEGMENTED_PAGED`, ali `SimulationConfig.validate()` ih odbija porukom
„… translation is not yet supported; use PAGED". Politika zamene stranica je samo FIFO
(`FIFOEvictionPolicy`), ali je iza apstrakcije `EvictionPolicy`.

---

## 2. Tehnološki stek

| Oblast | Tehnologija | Verzija | Gde se vidi |
|---|---|---|---|
| Jezik | Java | 25 (`maven.compiler.source/target` = 25, `<release>25`) | `vmsim/pom.xml` |
| GUI okvir | JavaFX (`javafx-controls`, `javafx-fxml`) | 26.0.1 | `pom.xml`, `module-info.java` |
| Čitanje konfiguracije | Jackson Databind + `jackson-dataformat-toml` | 2.18.2 | `SimulationConfig.loadFromFile` (`TomlMapper`) |
| Format konfiguracije | TOML | — | fajlovi u `config/*.toml` |
| Build | Apache Maven, `maven-compiler-plugin` 3.8.0 | — | `pom.xml` |
| Pokretanje / paketiranje | `javafx-maven-plugin` 0.0.8 (`javafx:run`, `javafx:jlink`) | 0.0.8 | `pom.xml` (mainClass `rs.ac.bg.etf.App`, jlink: `app_launcher`, `app_image`, `app_archive`) |
| Modularizacija | Java Platform Module System | — | `module-info.java` |
| Stilizovanje | JavaFX CSS (dve teme), generisanje skaliranih kopija u toku rada | — | `light-theme.css` (1322 reda), `dark-theme.css` (1064 reda), `fonts.css` |
| Fontovi | IBM Plex Sans i IBM Plex Mono, po 4 težine (400/500/600/700), SIL Open Font License | — | `resources/.../fonts/` (uključena licenca) |
| Internacionalizacija opisa koraka | `ResourceBundle` (`StepDescriptions.properties`) + `String.format` | — | `StepDescriptionFormatter` |
| Trajno čuvanje podešavanja | `java.util.Properties`, fajl `~/.vmsimulator/settings.properties` | — | `AppSettings` |

**Šta projekat namerno nema.** `pom.xml` deklariše samo četiri zavisnosti (dva JavaFX modula i dva Jackson modula).
Nema okvira za injekciju zavisnosti, nema dodatnih biblioteka za povezivanje svojstava (npr. EasyBind — iako ga
`CLAUDE.md` pominje kao primer stila), nema SceneBuilder/FXML ekrana u upotrebi i nema automatskih testova
(direktorijum `src/test` ne postoji).

**FXML.** Fajlovi `primary.fxml`, `secondary.fxml`, `PrimaryController`, `SecondaryController` i metode
`App.setRoot` / `App.loadFXML` su ostatak šablona projekta i nigde se ne koriste. Ceo korisnički interfejs se gradi
programski (Java kod), a stil dolazi iz CSS-a. `module-info.java` i dalje otvara paket `rs.ac.bg.etf` za `javafx.fxml`.

**Moduli** (`module-info.java`): modul `rs.ac.bg.etf` zahteva `javafx.controls`, `javafx.fxml`, `transitive javafx.graphics`,
`com.fasterxml.jackson.databind` i `com.fasterxml.jackson.dataformat.toml`; paketi `model.simulation` i `model.memory`
su otvoreni za `com.fasterxml.jackson.databind` (refleksija pri deserijalizaciji TOML-a u `SimulationConfig` i
`Instruction`), a izvozi se samo `rs.ac.bg.etf`.

**Jezičke mogućnosti koje kod aktivno koristi** (relevantno za obrazloženje izbora Jave 25): *record* tipovi
(`StepDescription`, `FrameMapping`, redovi tabela u ViewModel-ima, `PageTableDescriptorInit`, `InitialPage`),
*pattern matching* za `instanceof` (`viewModel.getContext() instanceof PageSimulationContext pageContext`),
*switch* izrazi (`MemoryAccessStep.getStepDescription`), generički tipovi sa ograničenim džokerima
(`SimulationStep<? extends SimulationContext>`), sekvencirane kolekcije iz Jave 21 (`ArrayList.addLast/getFirst/removeFirst`
u `FIFOEvictionPolicy`) i `Math.ceilDiv`.

**Obim koda** (`.java` fajlovi, glavna grana, sa nekomitovanim izmenama): 131 fajl, 17 809 redova.

| Sloj | Fajlova | Redova |
|---|---|---|
| `model` | 51 | 4 811 |
| `viewmodel` (uključujući `listeners`) | 18 | 2 581 |
| `view` (uključujući `util`, `tlb`, `inspector`, `os`, `config`, `shape`, `memory`) | 59 | 10 103 |
| koren (`App`, dva ostatka šablona) | 3 | 314 |

---

## 3. Arhitektura na visokom nivou

Aplikacija je organizovana po obrascu **MVVM** (Model–View–ViewModel) sa tri paketa pod `rs.ac.bg.etf`:

```
┌────────────────────────── View — paket view (JavaFX, 59 fajlova) ──────────────────────────┐
│ App → ResponsiveHost → { MainMenuView | ConfigurationView | SettingsView | SimulationView } │
│ SimulationView: TabPane {MMU | TLB | OS | Memory} + leva/desna bočna traka + inspektori     │
│ view.util: UiScale, ThemeCss, ResponsiveHost/Layout, PostLayoutTask, SchematicTab/Canvas,   │
│            WindowedTableView, WidthCalculator, FieldBoxes, StepDescriptionFormatter ...     │
└────────────────▲───────────────────────────────────────────────────────────────────────────┘
                 │ bind(), listeneri, ObservableList  (JavaFX svojstva)
┌────────────────┴────────────── ViewModel — paket viewmodel (18 fajlova) ───────────────────┐
│ AppViewModel (koordinator navigacije) · SimulationViewModel · MainMenuViewModel             │
│ ConfigurationViewModel · SettingsViewModel                                                  │
│ PagedMMUTabViewModel · PagedTLBTabViewModel · PagedOSTabViewModel · MemoryTabViewModel      │
└────────────────▲───────────────────────────────────────────────────────────────────────────┘
                 │ pozivi metoda i čitanje stanja (model je pasivan)
┌────────────────┴──────────────── Model — paket model (51 fajl) ───────────────────────────┐
│ simulation: Simulation · SimulationContext/PageSimulationContext · SimulationConfig         │
│             SimulationFactory · step.* (22 klase koraka)                                    │
│ tlb · os · table · memory · disk · settings                                                 │
└──────────────────────────────────────────────────────────────────────────────────────────────┘
```

**Pravila zavisnosti (proverena pretragom uvoza).**

- `model` ne uvozi ništa iz JavaFX-a, ni iz `view`, ni iz `viewmodel`. Zavisi samo od standardne biblioteke i
  (radi čitanja konfiguracije) od Jackson-a. Posledica je da se model može prevesti i izvršavati bez GUI-ja; to je
  i proveravano tokom pisanja ovog dokumenta (prevođenje samo paketa `model` uz Jackson, pa izvršavanje scenarija
  `showcase.toml` — 38 koraka — iz obične `main` metode).
- `viewmodel` zavisi od `model` i od JavaFX svojstava (`javafx.beans`, `javafx.collections`), ali ne od kontrola
  (`javafx.scene`). **Jedino odstupanje:** ViewModel klase uvoze `rs.ac.bg.etf.view.util.ValueConverter`
  (bezstanjsko formatiranje heksadecimalnih vrednosti) — jedina zavisnost `viewmodel → view`.
- `view` zavisi od `viewmodel` i (za tipove konfiguracije i opise koraka) od `model`.

**Model je pasivan i ne emituje događaje.** Osvežavanje tab-ViewModel-a pokreće jedan signal: svojstvo
`SimulationViewModel.currentStepNumberProperty()` (`IntegerProperty`), koje se postavlja posle svakog izvršenog
ili poništenog koraka. Svaki tab-ViewModel se pretplati na njega (`ChangeListener` u polju `stepListener`) i u
metodi `refresh()` iznova pročita stanje iz `PageSimulationContext` i istorije koraka. View se povezuje na
svojstva ViewModel-a; ne čita model direktno (izuzeci su prozori-inspektori, v. 6.2, koji primaju kontekst simulacije).

### 3.1 Životni ciklus aplikacije i navigacija

`App` (nasleđuje `javafx.application.Application`) u `start()`:

1. kreira `AppViewModel` i `ResponsiveHost` (koren scene),
2. učita sačuvanu temu iz `AppSettings` pre nego što se ijedna scena stilizuje,
3. pretplati se na `AppViewModel.currentScreenProperty()` — svaka promena ekrana poziva `handleScreenTransition`,
4. montira glavni meni, kreira scenu veličine 85 % vidljivog dela ekrana, primeni temu, maksimizuje prozor.

`AppViewModel` implementira četiri interfejsa za navigaciju (`MainMenuNavigationListener`,
`ConfigurationNavigationListener`, `SimulationNavigationListener`, `SettingsNavigationListener`) — svaki ViewModel
dobija samo interfejs kojim sme da traži prelazak, pa je `AppViewModel` u ulozi **medijatora**.
Stanja ekrana su u enumeraciji `ApplicationScreenState` (`MAIN_MENU`, `CONFIGURATION`, `SETTINGS`, `SIMULATION`).

Prelaz konfiguracija → simulacija (`AppViewModel.onConfigToSimulation`): `SimulationFactory.createSimulation(config)`,
`simulation.init()`, novi `SimulationViewModel`, uključivanje opcije „Resume" u glavnom meniju, prelazak na
`SIMULATION`. Ako bilo šta baci izuzetak, korisnik ostaje na ekranu konfiguracije. Povratak iz simulacije u glavni
meni **ne uništava** simulaciju (može se nastaviti); „New Simulation…" vodi na konfiguraciju.

---

## 4. Sloj modela

### 4.1 Konfiguracija — `SimulationConfig`

Konfiguracija je običan objekat (svojstva + `record` tipovi za ugnježdene strukture) koji se popunjava iz TOML
fajla pomoću Jackson-ovog `TomlMapper`-a. Bitne osobine učitavanja:

- `readerForUpdating(config)` uz `setDefaultMergeable(true)` — vrednosti iz fajla se **spajaju** u postojeći
  objekat, pa ekran konfiguracije može učitati fajl i zatim dozvoliti izmene polja iz forme;
- `ACCEPT_CASE_INSENSITIVE_ENUMS` (u TOML-u je `"paged"`, `"direct"` ...) i `READ_UNKNOWN_ENUM_VALUES_AS_NULL`.

Primer (isečak `config/showcase.toml`):

```toml
translationType = "paged"
pageBits = 3
wordBits = 6
physicalAddressBits = 8
addressableUnit = 4
numberOfUsers = 2
tlbType = "direct"
tlbSize = 8

[[instructions]]
user = 0
accessType = "WR"          # RD | WR | EX
virtualAddress = 0x55
value = 0x1234ABCD

[pageTables.0.1]           # korisnik 0, stranica 1
valid = false
dirty = false
block = 0

[[initialPages]]           # sadržaj stranice (ofset = vrednost)
userId = 0
page = 1
[initialPages.content]
21 = 0x11110015
```

Sadržaj `[[initialPages]]` završava u **fizičkoj memoriji** ako je deskriptor te stranice `valid = true`
(`getMemoryInit()`, na adresi `block << wordBits + ofset`), a na **disku** ako je deskriptor postojeći i nevalidan
(`getDiskInitContent()`).

**Izvedene veličine:** `getVirtualMemoryBits() = wordBits + pageBits`; fizička memorija ima `2^physicalAddressBits`
adresibilnih jedinica; broj okvira je `2^(physicalAddressBits − wordBits)`. Na ekranu se adrese prikazuju
heksadecimalno, sa `ceil(bitovi / 4)` cifara (`ValueConverter.hexDigitsFor`).

**Validacija (`validate()`, baca `InvalidConfig`).**

| Pravilo | Ograničenje |
|---|---|
| `physicalAddressBits`, `wordBits`, `pageBits` | > 0; `wordBits < physicalAddressBits` |
| `numberOfUsers`, `tlbSize` | stepen dvojke |
| `addressableUnit` | 1, 2, 4 ili 8 |
| broj bitova okvira | `physicalAddressBits − wordBits ≤ 32` (`MAX_FRAME_BITS`) |
| širina virtuelne adrese | `≤ 62` (`MAX_VIRTUAL_ADDRESS_BITS`), da stane u nenegativan `long` |
| broj stranica svih korisnika | `pageBits + log2(numberOfUsers) ≤ 32` (širina adrese diska, `diskBits`): svaka stranica svakog korisnika mora imati sopstveni disk blok |
| skupovno asocijativni TLB | `tlbEntriesPerSet` stepen dvojke i `≤ tlbSize` |
| početne stranice | korisnik i stranica u opsegu, ofseti < veličina stranice, vrednosti staju u jednu memorijsku reč |
| deskriptori tablice stranica | korisnik/stranica u opsegu, blok ≥ 0, blok unutar fizičke memorije, dva validna deskriptora ne smeju pokazivati na isti okvir |
| instrukcije | korisnik u opsegu, virtuelna adresa `< 2^virtualBits`, vrednost pri upisu staje u reč |
| prostor za jezgro | tablice stranica svih korisnika moraju stati u fizičku memoriju, svaka u **uzastopnim** okvirima, i mora ostati bar jedan okvir za korisničke podatke |

Neispravan primer za svako pravilo postoji kao poseban fajl `config/validation_*.toml` (v. 12).

### 4.2 Simulaciona mašina — `Simulation`, `SimulationContext`, koraci

Centralna ideja je da se izvršavanje jedne instrukcije razloži na **niz koraka**, gde je svaki korak objekat koji
zna (a) da se izvrši, (b) da se poništi i (c) koji korak dolazi posle njega.

```java
public abstract class SimulationStep<T extends SimulationContext> {
    public abstract SimulationStep<T> execute();      // izvrši i vrati SLEDEĆI korak
    public abstract void undo();                      // poništi efekte execute()
    public abstract StepDescription getStepDescription();
    public boolean isFirst() { return false; }        // true samo za InstructionFetchStep
    public final Set<SimulationComponent> getAffectedComponents();
}
```

`Simulation` drži trenutni korak i istoriju:

- `nextStep()` — izvrši `currentStep`, i **tek nakon uspešnog** `execute()` ga stavi na stek `stepHistory`
  (korak koji baci izuzetak ne ostavlja polovično primenjen unos na steku za poništavanje); u paralelnu listu
  `stepLog` dodaje opis koraka (izračunat jednom, u trenutku izvršavanja);
- `previousStep()` — skine korak sa steka, on ponovo postaje tekući (može se ponovo izvršiti) i pozove se `undo()`;
- pomoćne metode za navigaciju po instrukcijama: `isCurrentStepFirst`, `lastInstructionStartAtOrBefore`,
  `instructionStartStepNum`.

Ovo je istovremeno **obrazac Command** (svaki korak sa `execute`/`undo`) i **automat stanja** (svaki korak sam
kreira i bira sledeći, u zavisnosti od stanja sistema).
Kraj niza instrukcija signalizira `InstructionFetchStep.execute()` izuzetkom `NoSuchElementException`;
`SimulationViewModel` to prikazuje kao „End of instruction stream".

**Fabrika:** `SimulationFactory.createSimulation(config)` bira `PageSimulationContext` za `PAGED`. Za
`SEGMENTED`/`SEGMENTED_PAGED` grane su prazne (kontekst ostaje `null`) — zato je odbijanje tih tipova u
`validate()` obavezno.

**Kontekst.** `SimulationContext` (apstraktan) i `PageSimulationContext` (konkretan) drže sve promenljivo stanje:
memoriju, disk, TLB, tablice stranica po korisniku, upravljač okvirima (OS), pokazivač na tekuću instrukciju i
„skrečeve" (radne vrednosti) koje koraci međusobno razmenjuju. Inicijalizacija (`PageSimulationContext.init`):

1. `super.init()` — kreira `Memory` veličine `2^physicalAddressBits`, `DiskAddressGenerator`, `Disk`, upiše početni
   sadržaj memorije, kreira TLB traženog tipa (`initTLB`);
2. za svakog korisnika kreira `PageTable` i popuni deskriptore iz konfiguracije;
3. `PageOSMemoryManager.init(...)` — evidentira okvire koje već zauzimaju validne stranice iz konfiguracije;
4. za svakog korisnika `allocateAndLock(pagesPerPageTable)` — rezerviše i **zaključava** uzastopne okvire u kojima
   „leži" njegova tablica stranica; početna adresa (`pageTableStartAddresses`) je vrednost „page table pointer"-a;
5. `initDisk()` — upiše početni sadržaj diska.

**Veličina deskriptora** (`getPageTableDescriptorSize`): `2 + (physicalAddressBits − wordBits) + diskBits` bitova
(V, D, blok, disk; `diskBits` = 32), zaokruženo naviše na broj adresibilnih jedinica, pa na stepen dvojke — zato je
ofset deskriptora u tablici prosto **konkatenacija** (`page << log2(veličina deskriptora)`), što odgovara
hardverskoj šemi (ovo se i crta: „zero-fill" grana). Tablica stranica je zaista u fizičkoj memoriji:
`PageSimulationContext.getValueAtAddress` za adrese unutar regiona tablice stranica **sintetiše** sadržaj memorije iz
deskriptora u rasporedu `disk << (2 + frameBits) | block << 2 | D << 1 | V`, pa Memory inspektor prikazuje
konzistentne vrednosti.

**Konvencija: koraci upisuju u kontekst, ne izlažu getere.** Sve što neki potrošač (ViewModel) treba da zna o
efektu koraka nalazi se u polju konteksta (npr. `pageEvictionVictimUser/Page/Frame`, `evictedTlb*`,
`tlbWritebackUser/Page`, `currentLoadDiskAddress`) — postavlja ga `execute()`, a čita ViewModel. `instanceof`
nad tipom koraka sme da se koristi samo da se utvrdi *šta se desilo*. (Ova konvencija još nije sprovedena do
kraja: OS tab i dalje čita nekoliko geter-a sa `PageEvictionStep` i `PageStoreToDiskStep`; evidentirano u `bugs.md`.)

### 4.3 Katalog koraka (straničenje)

Ukupno 22 klase koraka: 7 apstraktnih opštih (paket `step`) i 15 konkretnih za straničenje (paket `step.page`);
opšti korak definiše šablon (**Template Method**: apstraktne `nextStep()`, `getTLBEntry()`, `writebackDirty()` ...),
a paged klasa popunjava specifičnosti straničenja — što ostavlja prostor za buduće segmentiranje.

| Korak (konkretna klasa) | Šta radi | Sledeći korak | Pogođene komponente |
|---|---|---|---|
| `PageInstructionFetchStep` | pomera pokazivač instrukcije, briše prethodnu fizičku adresu | `PageTLBLookupStep` | — |
| `PageTLBLookupStep` | računa ključ `(user << pageBits) \| page`, traži u TLB-u | pogodak: `PageTLBUpdateDirtyBitStep` ako je upis a unos čist, inače `PageFormPhysicalAddressFromTLBStep`; promašaj: `FormPageTableAddressStep` | TLB |
| `PageTLBUpdateDirtyBitStep` | postavlja D bit **u TLB unosu** | `PageFormPhysicalAddressFromTLBStep` | TLB |
| `PageFormPhysicalAddressFromTLBStep` | `PA = (block << wordBits) \| word` | `PageMemoryAccessStep` | TLB, MEMORY |
| `FormPageTableAddressStep` | adresa deskriptora = pokazivač na tablicu korisnika + ofset | `PageTableLookupStep` | MMU |
| `PageTableLookupStep` | čita deskriptor | validan: `PageTableUpdateDirtyBitStep` (upis i čist) ili `FormPhysicalAddressFromPageTableStep`; nevalidan: `PageFaultStep` | MMU |
| `PageTableUpdateDirtyBitStep` | postavlja D bit u deskriptoru (dolazi iz `PageTableLookupStep` za validnu stranicu ili iz `PageLoadIntoMemoryStep` za upis koji je izazvao page fault) | `FormPhysicalAddressFromPageTableStep` | MMU |
| `PageFaultStep` | traži slobodan okvir kod OS-a | nema slobodnog (`-1`): `PageEvictionStep`; inače `PageLoadIntoMemoryStep` | OS |
| `PageEvictionStep` | bira žrtvu (FIFO), poništava validnost njenog deskriptora, oslobađa okvir, poništava njen unos u TLB-u (i od njega preuzima D bit) | prljava: `PageStoreToDiskStep`; čista: `PageLoadIntoMemoryStep` | OS, MMU (+TLB ako je unos bio keširan) |
| `PageStoreToDiskStep` | upisuje sadržaj okvira žrtve na disk, briše D bit | `PageLoadIntoMemoryStep` | OS |
| `PageLoadIntoMemoryStep` | čita blok sa diska, upisuje ga u okvir, postavlja V i blok u deskriptoru, evidentira alokaciju | upis i čist deskriptor: `PageTableUpdateDirtyBitStep`; inače `FormPhysicalAddressFromPageTableStep` | OS, MMU, MEMORY |
| `FormPhysicalAddressFromPageTableStep` | `PA = (block << wordBits) \| word` iz deskriptora | `PageTLBEvictionStep` ako je slot/skup pun (`wouldEvict`), inače `PageTLBUpdateStep` | MMU, MEMORY |
| `PageTLBEvictionStep` | oslobađa unos TLB-a; ako je prljav, **upisuje D bit nazad u deskriptor** te stranice | `PageTLBUpdateStep` | TLB (+MMU ako je bilo povratnog upisa) |
| `PageTLBUpdateStep` | umeće novi TLB unos (V, D i blok kopira iz deskriptora) | `PageMemoryAccessStep` | TLB |
| `PageMemoryAccessStep` | RD: čita vrednost; WR: upisuje (pamti staru vrednost); EX: proverava opseg | `PageInstructionFetchStep` | MEMORY |

Skup `getAffectedComponents()` koristi `SimulationViewModel` za **oznake obaveštenja na tabovima** (v. 5.2).

**Dijagram toka jedne instrukcije:**

```
InstructionFetch → TLBLookup ──pogodak──► [TLBUpdateDirtyBit: samo WR i čist unos] ─► FormPA(TLB) ─► MemoryAccess ─► (sledeći Fetch)
                       │promašaj
                       ▼
             FormPageTableAddress → PageTableLookup ──validan──► [PTUpdateDirtyBit: samo WR i čist] ─► FormPA(PT)
                                          │nevalidan                                                     │
                                          ▼                                                              ▼
                                      PageFault ──slobodan okvir──► PageLoadIntoMemory ─► [PTUpdateDirtyBit: samo WR] ─► FormPA(PT) ─► [TLBEviction] ─► TLBUpdate ─► MemoryAccess
                                          │nema okvira                       ▲
                                          ▼                                  │
                                     PageEviction ──čista──────────────────┤
                                          │prljava                          │
                                          └──► PageStoreToDisk ─────────────┘
```

### 4.4 Poništavanje (undo)

Mogućnost kretanja unazad je ključna za edukativnu vrednost i osnovni izazov modela. Ne čuva se „snimak celog
sistema" po koraku; umesto toga **svaki korak pamti samo ono što je promenio** i vraća to u `undo()`:

| Korak | Šta pamti za `undo()` |
|---|---|
| `MemoryAccessStep` | prethodna vrednost adrese (samo za WR) |
| `PageLoadIntoMemoryStep` | prethodni sadržaj okvira (`previousBlock`), prethodni broj bloka u deskriptoru; ne čita `context.currentDescriptor` iz `undo()` |
| `PageStoreToDiskStep` | prethodni sadržaj bloka na disku |
| `PageEvictionStep` | mapiranje žrtve (`FrameMapping`), prethodni D bit deskriptora; TLB unos se vraća na isti slot |
| `PageFaultStep` | prethodni tekući deskriptor konteksta (deljena radna promenljiva — vraća se stara vrednost, ne briše) |
| TLB (`TLB` bazna klasa) | tri steka zapisa: `InvalidationRecord`, `InsertionRecord`, `EvictionRecord` (pozicija + vrednost pokazivača zamene) |
| `PageOSMemoryManager` / `FIFOEvictionPolicy` | `undoAllocation`, `undoFree`, `undoVictim` |
| `InstructionFetchStep` | prethodna fizička adresa i pomeranje pokazivača instrukcije unazad |

Posledice: (1) `undo()` se poziva strogo u obrnutom redosledu izvršavanja (LIFO) — `FIFOEvictionPolicy.undoAllocation`
zato jednostavno uklanja poslednji element reda; (2) isti objekat koraka može se **izvršiti ponovo** posle poništavanja,
pa `execute()` mora biti samostalan (`setAffectedComponents` zato *zamenjuje*, a ne dopunjuje skup);
(3) vrednosti potrebne za opis koraka hvataju se u `execute()`, a ne čitaju iz deljenog konteksta u trenutku
prikaza (uzrok ispravljenih grešaka u `bugs.md` vezanih za višestruko poništavanje).

### 4.5 TLB

`TLB` je apstraktna klasa koja je nezavisna od strukture adrese: radi sa „adresnom komponentom" (broj stranice, u
budućnosti i segmenta) i identifikatorom procesa, spojenim u ključ:

```
ključ = (processId << addressBits) | addressComponent      (pomeranje se izvodi u long-u)
```

Tri konkretne implementacije (sve čuvaju `entries` kao `ArrayList<TLBEntry>` fiksne veličine `tlbSize`;
prazan slot je `null`):

| | `AssociativeTLB` | `DirectTLB` | `SetAssociativeTLB` |
|---|---|---|---|
| Ključ → slot | bilo koji slot | `ključ mod size` | skup: `ključ mod numSets`, unutar skupa bilo koji način |
| Broj bitova indeksa (`getIndexComponentBits`) | 0 | `log2(size)` | `log2(numSets)` |
| Sačuvani tag | ceo ključ | `ključ >>> log2(size)` | `ključ >>> log2(numSets)` |
| Pretraga | linearna po svim unosima | jedan slot | linearna unutar skupa |
| Zamena | „glupi" kružni pokazivač (round-robin), jedan | nema izbora (slot je određen) | „glupi" kružni pokazivač **po skupu** |
| Raspored u memoriji | redom | redom | **way-major**: način `w` skupa `s` je na `w · numSets + s` |

`TLBEntry` je jedinstvena klasa unosa i već sadrži polja za segmentaciju (`rwe`, `length`, `startAddr`), pa
proširenje na segmentirani režim ne traži novu klasu.

**Protokol umetanja i izbacivanja (bitan za vizuelizaciju).** Umesto da izbaci unos u trenutku umetanja, TLB izlaže
tri odvojene operacije, jer se u simulatoru izbacivanje prikazuje kao poseban korak:
`wouldEvict(tag)` (bez izmena stanja, da li je odgovarajući slot/skup pun) → `evictForInsertion(tag)` (vraća žrtvu i
pomera kružni pokazivač — kod direktno preslikanog TLB-a pokazivača nema, pa je to samo uvid u slot — ali **unos
ostaje u svom slotu**, samo ga pozivalac markira nevažećim) → `insert(entry)`
(nevažeći slot smatra slobodnim). Zato ostaje da se u šemi vidi šta je izbačeno. Odvojena operacija
`invalidateEntry(tag)` (koristi je `PageEvictionStep`) unos zaista uklanja, uz `InvalidationRecord` za poništavanje.

### 4.6 Tablica stranica, memorija, disk

- **`PageTable`** — po korisniku; `HashMap<Long, PageTableDescriptor>`. Deskriptor se pravi **lenjo** pri prvom
  pristupu (`getEntryAndAdd`), pa tablica sa `2^pageBits` ulaza (potencijalno milijardama) zauzima samo dodirnute
  ulaze. `PageTableDescriptor` sadrži `valid`, `dirty`, `block`, `disk` i `page`.
- **`Memory`** — `TreeMap<Long, Long>` (adresa → vrednost); nepostojeća adresa čita se kao 0; provera opsega
  poredi adresu **bez znaka** (`Long.compareUnsigned`). Blokovske operacije (`readBlock`, `writeBlock`) rade nad
  podmapom `[address, address + size)`, pri čemu `writeBlock` prvo briše ceo opseg (jer je blok redak).
- **`Disk`** — `HashMap<Long, SortedMap<Long, Long>>` (adresa bloka → redak sadržaj stranice).
- **`DiskAddressGenerator`** — deterministička funkcija koja svakom paru (korisnik, stranica) dodeljuje 32-bitnu
  adresu bloka na disku: ulaz `korisnik · maxPages + stranica` se pomeri za „seme", pa prolazi kroz
  **Fajstelovu mrežu** sa 4 kruga i 16-bitnim polovinama. Time su adrese na disku raspršene (izgledaju kao pravi
  diskovi), a da nije potrebna tabela dodele. Funkcija je permutacija nad `[0, 2^32)`, pa različite stranice nikad ne dobijaju
  isti blok — dok god ih ima najviše `2^32`, što obezbeđuje pravilo u `validate()` (v. 4.1). Seme se izvodi iz konfiguracije
  (`generateDiskSeed()`: `Objects.hash(translationType.name(), wordBits, pageBits, segmentBits, numberOfUsers,
  physicalAddressBits)`); koristi se **ime** enuma, a ne sâm enum, jer je `Enum.hashCode()` identitetski heš koji JVM ne
  čuva isti između pokretanja. Tako je seme (a s njim i svaka adresa na disku) čista funkcija konfiguracije.

### 4.7 OS: upravljanje okvirima i zamena stranica

- **`PageOSMemoryManager`** — `TreeMap<Long, FrameMapping>` zauzetih okvira (`FrameMapping` je `record (user, page,
  descriptor)`; za okvire jezgra je vrednost `null`), skup zaključanih okvira (tablice stranica), i `maxFrames =
  1L << (physicalAddressBits − wordBits)`. Slobodan okvir se traži **šetnjom kroz zauzete okvire u rastućem redosledu**
  dok se ne nađe prva „rupa" (`getFreeFrame`, `allocateAndLock`) — nikada petljom po svim okvirima.
- **`EvictionPolicy`** (apstraktna): `logAllocation`, `getVictim`, `removeVictim`, `undoVictim`, `undoAllocation`,
  `getOrder`; **`FIFOEvictionPolicy`** je jedina implementacija (`ArrayList<Long>` kao red okvira, najstariji prvi).
  Okviri koje popunjavaju validne stranice iz konfiguracije upisuju se u red u rastućem redosledu broja okvira, da
  početni redosled izbacivanja bude determinističan.
- Klasa `OS` je prazna (rezervisano mesto).

### 4.8 Retke strukture — princip

Sve veličine koje se skaliraju sa širinom adrese (okviri, stranice, adrese memorije i diska) mogu biti reda
milijardi, zato **nijedan** deo sistema ne zauzima jedan objekat/element po takvom entitetu: `Memory` je
`TreeMap`, `Disk` i `PageTable` su `HashMap`, `PageOSMemoryManager` je `TreeMap` zauzetih okvira. Broj koji potiče
od širine u bitovima nikada se ne koristi kao granica petlje ili veličina niza. (Isti princip u prikazu:
poglavlje 7.) Ograničenja koja to omogućavaju su u `validate()`: do 32 bita okvira, do 62 bita virtuelne adrese i
najviše `2^32` stranica (svih korisnika zajedno), jer je adresa diska široka 32 bita.

---

## 5. Sloj ViewModel-a

Svi ViewModel-i izlažu stanje **isključivo kao JavaFX svojstva** (`IntegerProperty`, `StringProperty`,
`BooleanProperty`, `ObjectProperty`, `LongProperty`, `ObservableList`) sa pristupnicima `xxxProperty()`, da bi se
View povezao (`bind`, `bindBidirectional`, `Bindings`) umesto da ih periodično čita. Vrednosti koje još nisu
poznate (npr. fizička adresa pre nego što je formirana) izlažu se kao string `"/"`, a View iz toga izvodi „zamagljen"
prikaz.

### 5.1 Ekrani izvan simulacije

- **`MainMenuViewModel`** — `resumeAvailableProperty()`; delegira navigaciju.
- **`ConfigurationViewModel`** — svojstva za sva polja forme (`wordBits`, `physicalAddressBits`, `pageBits`,
  `tlbType`, ...), tri `ObservableList` (`InstructionEntry`, `PageTableEntry`, `MemoryInitEntry`) koje uređuju
  posebni prozori, i `validationErrorMessage`. `validateAndLaunch()` sastavlja `SimulationConfig` (ili ažurira
  učitani), „spljošteno" iz lista vraća u `Map`/`List` strukture konfiguracije, poziva `validate()`, pa bilo šalje
  konfiguraciju navigatoru bilo upisuje poruku greške. `loadConfigFromFile` popunjava formu iz TOML-a.
- **`SettingsViewModel`** — `themeProperty()` je izvor istine za temu; svaka promena se odmah čuva (`AppSettings`).

### 5.2 `SimulationViewModel` — spoljna ljuska radnog prostora

Upravlja koracima i objedinjuje stanje koje prikazuju bočne trake:

- **Upravljanje izvršavanjem:** `executeNextStep`, `executeNextInstruction` (do sledećeg `isFirst()` koraka),
  `executeToEnd`, `executePreviousStep`, `revertToStep(n)`, `revertToInstructionStart`, `goToInstructionStart(i)`,
  `restart` (= vraćanje na korak 0 poništavanjem, ne ponovnom inicijalizacijom).
  Posle svakog poziva `syncFromSimulation()` osvežava brojač koraka (koji budi pretplaćene tab-ViewModel-e).
- **Dnevnik:** `ObservableList<StepDescription> logEntries` — održava se **inkrementalno** u korak sa
  `Simulation` (dodavanje/uklanjanje na kraju), a ne ponovnim izračunavanjem.
- **Opisi bez lokalizacije u modelu:** korak vraća `StepDescription(StepDescriptionKey key, Object... args)`
  (`record`); tekst nastaje tek u View-u (`StepDescriptionFormatter` + `StepDescriptions.properties`). Argument
  koji je i sam `StepDescription` je „fraza" (npr. „TLB entry 1 (set 0)") i formatira se u mestu, pa je npr.
  „odakle je blok pročitan" formulisano na jednom mestu. Postoji samo osnovni (engleski) paket resursa;
  tekstovi interfejsa van opisa koraka su trenutno napisani direktno u kodu.
- **Obaveštenja na tabovima (bedževi):** kada tab nije u fokusu, a poslednji izvršeni korak je zahvatio njegovu komponentu
  (`getAffectedComponents()`), na tabu se pojavljuje tačka. Pravila (`updateTabNotifications`): bedž je aktivan ako je
  komponenta *relevantna sada*, tab nije fokusiran i pojava nije „potvrđena"; fokusiranje taba potvrđuje pojavu;
  prelaz „nije relevantna → relevantna" je nova pojava i poništava prethodnu potvrdu.
- **Fokus:** `selectedComponentProperty()` — pamti izabrani tab (View ga ponovo otvara posle prebuilding-a, v. 8).

### 5.3 Tab-ViewModel-i (MMU, TLB, OS, Memory)

Zajednički obrazac (`PagedMMUTabViewModel`, `PagedTLBTabViewModel`, `PagedOSTabViewModel`, `MemoryTabViewModel`):

1. konstruktor jednom izvede fiksne širine (bitove stranice, reči, okvira, diska) — obična `final int` polja, jer se
   ne menjaju tokom simulacije;
2. pretplati se na `currentStepNumberProperty()` preko imenovanog `stepListener`-a (da se u `dispose()` može ukloniti);
3. `refresh()` izračuna prikaz: heksadecimalne stringove, vidljive redove, „upaljene" žice, bočnu belešku;
4. `dispose()` skida slušaoca.

**Paljenje žica** (`recomputeActiveLines`). Nema pamćenja „upaljenih" žica: pri svakom osvežavanju ViewModel
prolazi istoriju koraka **unazad do poslednjeg `InstructionFetchStep`** i za svaki korak (`linesFor(step)`) doda
skup žica koje taj tip koraka dotiče. Zato žica ostaje upaljena do kraja instrukcije, a poništavanje koraka
automatski „gasi" žicu — nema stanja koje bi trebalo dodatno vraćati. Enumeracije žica: `MmuLine` (8),
`TlbLine` (5), `OsLine` (3). Stanje se u CSS-u prikazuje pseudo-klasom `:active`.

**Prozori nad velikim skupovima podataka (windowed).** `PagedOSTabViewModel` ne pravi red po okviru: drži
`LongProperty windowStart` (pokriva ceo `long` opseg okvira), `frameRowAt(long)` gradi **jedan** red na zahtev, a
`framesRevisionProperty()` obaveštava View da iscrta prozor ponovo. `PageTableView` (MMU) prikazuje 7 unosa
(`WINDOW_SIZE = 7`) centriranih oko adresirane stranice, Memory tab 15 reči (`WINDOW_SIZE = 15`), TLB tab do 5
redova (`MAX_VISIBLE_ROWS = 5`).

**Bočne beleške** (`MmuSideNote`, `TlbSideNote`) — kartice koje prikazuju promenu na unosu *izvan* vidljivog prozora
(npr. izbačena stranica ili povratni upis D bita), u obliku reda tabele, ne proze. Mogu se zatvoriti; ostaju
zatvorene za istu pojavu (`lastNoteStep`).

**TLB tab-ViewModel** ima dodatnu, paralelnu putanju za skupovno asocijativni TLB: jednu listu redova po načinu
(`getWayRows(w)`), zajednički izabrani red i `resolvedWayProperty` (način koji je pogođen/umetnut, ili `-1`).
Oznake redova: `RowHighlight {NONE, HIT, MISS, INSERT}`; ishod pretrage za bojenje taga: `LookupOutcome`.

---

## 6. Sloj prikaza (View)

### 6.1 Ekrani i radni prostor

`MainMenuView`, `ConfigurationView`, `SettingsView` i `SimulationView`. Ekrani se **grade programski**, bez FXML-a.
Prozor sa konfiguracijom koristi `ComboBox` i `TextField` povezane dvosmerno (`bindBidirectional`) sa
svojstvima ViewModel-a, uz `FileChooser` za učitavanje TOML fajla; tri obimna dela konfiguracije (instrukcije,
tablice stranica, početni sadržaj memorije) uređuju se u posebnim prozorima (`view.config`:
`InstructionsEditorWindow`, `PageTablesEditorWindow`, `MemoryInitEditorWindow` nad zajedničkom osnovom
`ConfigEditorWindowSupport`).

**`SimulationView`** je radni prostor:

```
┌ MenuBar: Simulation (Restart / Skip to End / New Simulation…) · View (inspektori) ───────────────┐
├──────────────┬──────────────────────────────────────────────────────┬─────────────────────────────┤
│ leva traka   │ TabPane:  MMU │ TLB │ OS │ Memory   (lenji tabovi)    │ desna traka                 │
│ • Back       │  — šematski prikazi (poglavlje 6.3–6.4) —              │ • Execution Log (ListView)  │
│ • lista      │                                                        │ • Step Description          │
│   instrukcija│                                                        │ • Step: N  ◀ Previous Next ▶│
│ • trenutna VA│                                                        │ • Instruction: k            │
│ • trenutna PA│                                                        │   ◀ Previous Next ▶         │
└──────────────┴──────────────────────────────────────────────────────┴─────────────────────────────┘
```

Tri panela su u `SplitPane` čiji se razdelnici pri svakoj promeni širine ponovo postavljaju na projektovane
proporcije (18 % / 82 %), jer `SplitPane` pamti pozicije kao promenljivo stanje koje minimalne veličine „guraju".
Klik na instrukciju u levoj traci poziva `goToInstructionStart`; klik na korak u dnevniku poziva `revertToStep`.
Lista instrukcija je običan `VBox` u `ScrollPane`, a ne `ListView`, jer je broj instrukcija ograničen ljudskim
unosom (nije veličina reda adresnog prostora). Opis koraka ima fiksnu visinu, pa dugmad ne „skaču" pri promeni teksta.

**Lenji tabovi** (`lazyTab`): pri izgradnji ekrana gradi se samo izabrani tab, ostali pri prvom prikazivanju —
svaki tab je šema od stotina čvorova, a ceo ekran se iznova gradi pri svakoj promeni skale (v. 8).

**`dispose()`.** `SimulationView` beleži sve što je registrovao na dugovečnim ViewModel-ima u listi `teardown` i
uklanja to u `dispose()`; bez toga bi svaki ponovni izgrađeni ekran ostao vezan za ViewModel-e i curio memoriju.

### 6.2 Prozori-inspektori

Pet zasebnih prozora (`view.inspector`, `view.os`) za pregled celih struktura: **Page Table**, **TLB**, **Memory**,
**Disk Block** i **Replacement Queue**. Otvaraju se iz menija *View* ili iz odgovarajućeg taba (npr. Disk Block iz
kutije „Disk" na OS tabu). Svi se grade kroz `InspectorWindows.build(...)` (v. 8.6) i zatvaraju se zajedno kada se
napusti ekran simulacije (`InspectorWindows.closeAll()`). Svih pet inspektora koristi generičku `WindowedTableView` (v. 7).

### 6.3 Šematski tabovi — `SchematicTab` i `SchematicCanvas`

MMU, TLB, OS i Memory tab nasleđuju `SchematicTab` (`StackPane`) koji montira `SchematicCanvas` (`Pane` sa
apsolutnim koordinatama) u `ScrollPane` sa `fitToWidth` i `fitToHeight`: platno popunjava vidljivi prostor, ali nikad
nije manje od svoje **projektne veličine**.

- **Projektna veličina** (`designWidthProperty`, `designHeightProperty`) je deklarisan minimum koji tab izvodi iz
  onoga što je stvarno nacrtao (desna ivica tabele + slobodan prostor za žicu + PA kutije + margina), a `SchematicTab`
  ga izveštava naviše kao svoju minimalnu veličinu. Ne sme zavisiti od trenutne širine/visine platna (Physical Address
  je vezan za širinu platna — inače bi se merenje beskonačno uvećavalo). Za merenje se koristi `getLayoutBounds()`, ne
  `boundsInParent` (zamućenje tabele dok je u „magli" menja `boundsInParent`); postoji i determinističko rezervno
  merenje za tab koji nikad nije bio prikazan.
- **Promena minimalne veličine** se javlja odloženim, spojenim `requestLayout()` na *svim* precima (zahtev bi se inače
  izgubio na prvom nemenadžovanom pretku — sadržaj `TabPane`-a).
- **Tab čija visina raste sa konfiguracijom** (skupovno asocijativni TLB: jedna tabela po načinu) predefiniše
  `heightToFit(...)`, pa radni prostor traži samo visinu vrha šeme plus jednog načina, a ostatak se **skroluje** umesto
  da se ceo interfejs smanji do najmanje skale.

**`PostLayoutTask`** — ponovno usmeravanje žica. Žice zavise od rasporeda (položaj kutija, širine natpisa), a on se
menja desetinama svojstava po jednom ciklusu iscrtavanja pri promeni veličine prozora. Zato ponovno usmeravanje nije
`Platform.runLater` po svakoj promeni, već posao koji se **spaja u jedan po ciklusu** i izvršava odmah posle prolaza
rasporeda, a pre sinhronizacije čvorova za iscrtavanje (`Scene.addPostLayoutPulseListener`) — žice i kutije se iscrtavaju
u istom kadru. Poslovi moraju biti idempotentni; tab koji nije prikazan preskače se dok predak ne postane vidljiv;
`flushPending()` izvršava sve pending poslove odmah (koristi se pri montiranju ekrana).
`SchematicCanvas.bringToFront(...)` podiže žice u jednom pozivu i ne radi ništa ako su već na vrhu istim redom
(`Node.toFront()` bi svaki put menjao listu dece).

**Žice — `BitWidthLine`** (`view.shape`). Jedini dozvoljeni način crtanja žice u šemama: `Group` koji sadrži liniju,
strelicu, kosu crtu sa oznakom širine („Nb") i opcionu vrednost; sve krajnje tačke su `DoubleProperty` i vezuju se za
kutije koje povezuju, pa se žica sama preračunava. „Lakat" je lanac više `BitWidthLine` segmenata. `CurlyBrace` je
vektorski oblik vitičaste zagrade (spajanje `user` + `page` u ključ `k@p`). `FieldBoxes` je zajednička fabrika kutija
polja (Page | Word, Block | Word ...) i njihovih naslova, tako da MMU i TLB tab izgledaju identično.

### 6.4 Sadržaj tabova

(Struktura i imena su iz klasa `view`/`viewmodel`; opis vizuelnog ponašanja žica i redova potiče iz komentara u kodu i
iz odeljaka „MMU & TLB tabs" u `CLAUDE.md`.)

**MMU** (`PagedMMUTabView`). Virtuelna adresa `Page | Word`, fizička `Block | Word`; `Page` se pomera ulevo za
`shiftBits` (zero-fill) i spaja u ofset deskriptora; ofset i pokazivač na tablicu stranica ulaze u sabirač čiji izlaz
je adresa deskriptora; tablica stranica (`PageTableView`) prikazuje 7 unosa oko adresirane stranice, a polja V / D /
Disk / Block izabranog reda se spuštaju na samooznačavajuće žice ispod tabele. Mapiranje koraka na žice: `FormPageTableAddressStep` →
stranica/zero-fill/ofset/pokazivač; `PageTableLookupStep` → sabirač→red; `FormPhysicalAddressFromPageTableStep` →
prolaz reči + red→blok. Putanja TLB-pogotka namerno je „mračna" na ovom tabu.

**TLB** (`PagedTLBTabView`). Polja `Process(User)` i `Page | Word` (odnosno `Block | Word`); `user` i `page` se spajaju
vitičastom zagradom u ključ `k@p`. Telo tabele bira se prema `tlbType`: `AssociativeTLBView` (kratki izvodi po redu u
jednu magistralu paralelne pretrage), `DirectTLBView` (jedna prozorska tabela) ili `SetAssociativeTLBView` (jedna tabela
po načinu). Sva tri implementiraju interfejs **`TLBBodyView`** koji izlaže samo *orijentire* rasporeda
(`addressAnchorProperty`, `tableBottomAnchorProperty`, `activeProperty`, `syncAnchors()`), a tab odlučuje kako se
adresa razdvaja i odakle izlazi blok — telo je nezavisno od tipa preslikavanja. Za direktni i skupovno asocijativni TLB
ključ se račva: visokih `k@p − m` bitova (**tag**) završava u očitavanju obojenom prema ishodu pretrage
(`.tlb-tag-hit/miss`), a niskih `m` bitova (**indeks/skup**) ulazi u telo. Redovi se boje zeleno (pogodak), crveno
(promašaj), plavo (umetanje). Kod skupovno asocijativnog TLB-a ceo skup je označen, pri čemu je pogođeni/umetnuti
način zelen/plav, a ostali crveni; izlaz bloka ide u zajedničku magistralu i jednim usponom u polje `Block`.

**OS** (`PagedOSTabView`). Tabela okvira (`FrameTableView`: Frame / State / User / Page / V / D / Disk, prvi stubac je
traka zauzeća), kutija „Disk" sa dve žice (učitavanje/povratni upis) i klikom na inspektor bloka, FIFO red zamene
(`ReplacementQueueView`: HEAD/TAIL, sledeća žrtva) i pregled po korisniku (`UserSummaryView`: pokazivač na tablicu i
broj validnih stranica). Stanja okvira: `FREE`, `ALLOCATED`, `KERNEL`, `VICTIM`, `EVICTING`.

**Memory** (`MemoryTabView`, `MemoryTableView`). Prozor od 15 reči fizičke memorije centriran oko trenutno adresirane;
„zamagljen" dok adresa nije formirana; žica ka Physical Address ostaje mračna dok je polje PA `"/"`.

---

## 7. Skalabilnost prikaza: prozorske tabele

`ListView` (`VirtualFlow`) i pored virtuelizacije prave alokacije proporcionalne veličini modela i izlazi iz memorije
pri skrolovanju već pre `Integer.MAX_VALUE` (tvrdnja iz `CLAUDE.md`; u `bugs.md` je zabeležen konkretan slučaj: OS tab je
pravio jedan objekat-red po fizičkom okviru i pucao zbog nedostatka memorije). Zato se sve tabele nad veličinama iz adresnog
prostora (okviri, stranice tablice, reči memorije, blokovi diska) prave kao **prozorske tabele**:

- `WindowedRowSource<R>` — interfejs sa `long getEntryCount()` i `R rowAt(long index)`; red se gradi na zahtev;
- `WindowedTableView<R>` — komponenta sa **fiksnim skupom čvorova-redova** (broj vidljivih redova računa se iz stvarne
  visine, najmanje 3), sopstvenim `ScrollBar`-om, točkićem miša i (opcionim) poljem za skok na traženi unos;
- `AddressScaleScrollBar` — `ScrollBar` čiji se opseg preslikava na ceo `long` prostor: traka ima fiksni virtuelni opseg
  od 1 000 000 i odnos palca ograničen na 5–40 %, pa palac ne postaje nevidljiv za milijarde unosa niti prekrupan za
  nekoliko;
- adapteri po strukturi: `PageTableRowSource`, `MemoryRowSource` (do `2^physicalAddressBits` reči, čita se preko
  `getValueAtAddress`), `TLBRowSource`, `DiskBlockRowSource`; `FrameTableView` je posebna, ugrađena varijanta iste ideje
  sa `PagedOSTabViewModel.windowStartProperty()`.

Pošto ViewModel ne materijalizuje redove, nijedna tabela ne zavisi od veličine adresnog prostora — samo od broja
vidljivih redova.

---

## 8. Responzivni interfejs bez skalirajuće transformacije

Najneuobičajenija odluka u projektu, i najveći deo `view.util` paketa. Interfejs je projektovan za **1080p preko celog
ekrana = skala 1.0**. Uobičajeno rešenje — skalirajuća transformacija scene (`Scale`, `setScale*`) — je isprobano i
odbačeno: transformisana scena gubi *hinting* i ClearType (tekst je zamućen) i smešta jednopikselske ivice između
piksela uređaja, pa je slika mekana pri svakoj skali osim 1.0, a `Window.renderScale` ne pomaže jer menja rezoluciju
rastera, ne raspored. Umesto toga, **interfejs se ponovo raspoređuje (i ponovo gradi) na drugoj skali**, tako da se
svaki font i svaka dužina množe faktorom i zaokružuju na cele piksele — tekst se iscrtava u svojoj pravoj veličini.

### 8.1 Komponente

| Klasa | Uloga |
|---|---|
| `UiScale` | jedini faktor skale; `px(dužina)` i `font(veličina)` prevode projektne (1080p) mere u stvarne; pravila zaokruživanja: dužine `round(d·f)` (nikad 0), fontovi `max(min(d, 8), round(d·f))` (najmanje 8 px); pri `f = 1.0` ništa se ne zaokružuje i ne prepisuje |
| `ThemeCss` | regularnim izrazima prepisuje CSS teme: svaka `px` vrednost u `-fx-*` deklaracijama množi se istim pravilima kao u `UiScale`; `-fx-stroke-width` čuva četvrtinu piksela; radijusi efekata i bezjedinične vrednosti ostaju |
| `ResponsiveLayout` | sve numeričke konstante politike skale (v. 8.2) |
| `ResponsiveHost` | koren scene: daje ekranu ceo prozor, meri njegov minimum, zahteva ponovno građenje pri promeni željene skale |
| `PostLayoutTask` | (v. 6.3) sve što se odlaže do kraja rasporeda ne sme biti `Platform.runLater` |
| `WidthCalculator` | širine kolona iz metrike fonta (`Text` sa „IBM Plex Mono"), uz padding koji prati skalu |

Pošto se faktor menja **samo između izgradnji ekrana**, svaki pogled može da ga tretira kao konstantu za svoj
životni vek: skalirane dužine su **instancna polja** (u `UPPER_CASE` radi čitljivosti), nikada `static`. Za
svaku skalu različitu od 1.0 generiše se kopija CSS-a teme (u privremenom direktorijumu `vmsim-theme-*`, keširana po (tema, faktor));
`fonts.css` je izdvojen jer skalirane kopije žive u drugom direktorijumu, pa relativni `url()` u `@font-face` ne bi radili.
Fontovi se učitavaju **jednom** (`Font.loadFont` bi pri svakom pozivu iznova kopirao i registrovao datoteku); `UiScale.applyTheme`
menja samo stavke liste stilova koje se razlikuju (`setAll` bi ponovo učitao `fonts.css`).

### 8.2 Politika skale (`ResponsiveLayout`)

| Konstanta | Vrednost | Značenje |
|---|---|---|
| `REFERENCE_WIDTH × HEIGHT` | 1920 × 1080 | projektni prozor, skala 1.0 |
| `MIN_SCALE` / `MAX_SCALE` | 0.6 / 2.0 | granica čitljivosti / plafon na velikim ekranima |
| `STEP` | 0.02 | granularnost skale (svaka promena je višekratnik) |
| `FIT_MARGIN` | 0.015 | dodatni prostor pri izboru skale i veličina koraka smanjivanja |
| `MIN_WINDOW_MARGIN` | 0.03 | isto, za najmanji dozvoljeni prozor |
| `RESCALE_DELAY_MILLIS` / `RESCALE_INTERVAL_MILLIS` | 10 / 0 | zakašnjenje / minimalni razmak između građenja |
| `SETTLE_PASSES` | 4 | krugova „CSS + raspored + preusmeravanje žica" pre prvog kadra |
| `MAX_SCALE_SWITCHES` | 4 | najviše uzastopnih promena skale pri montiranju |
| `INITIAL_WINDOW_FRACTION` | 0.85 | početni prozor kao deo vidljivog ekrana |
| `MIN_WINDOW_WIDTH × HEIGHT` | 640 × 480 | apsolutni minimum prozora |

`targetScale(širina, visina, projektniMinimumŠirina, projektniMinimumVisina)` daje skalu koja odgovara prozoru:
ako minimum ekrana ne staje pri 1.0, skala se smanjuje dok ne stane (ne ispod `MIN_SCALE`); u suprotnom ostaje 1.0
sve dok prozor ne prevaziđe referentni u obe dimenzije, a onda raste sa prozorom do `MAX_SCALE`. Rezultat se uvek
zaokružuje **naniže** na višekratnik od `STEP`.

### 8.3 Petlja povratne sprege

1. Ekran izveštava zahtevani prostor **samo** preko minimalne veličine svog korena (`minWidth/minHeight`);
   `SimulationView` je gradi eksplicitno (zbir minimuma traka i tabova + visina zaglavlja tabova), jer `TabPaneSkin`
   ne prosleđuje minimume sadržaja, a `ScrollPane` skriva minimum svog sadržaja.
2. `ResponsiveHost.layoutChildren` deli izmerene minimume skalom pri kojoj je ekran građen (→ projektni minimum,
   koji samo **raste** dok je isti ekran prikazan, radi stabilnosti) i pita `targetScale`.
3. **Asimetrija:** skala se **povećava** kad god projektni minimum kaže da ima mesta, ali se **smanjuje** samo kad
   *stvarno izmereni* minimum pri tekućoj skali prevazilazi prozor (`wantedScale`). Fontovi se prave u celim pikselima,
   pa izmereni minimum izađe nekoliko procenata veći od procene; izvođenje iz procene vodilo bi u beskonačno
   smanjivanje ekrana koji staje.
4. Ako se željena skala razlikuje od tekuće, posle 10 ms (jedan puls) `App.changeScale` menja faktor i temu i
   **ponovo gradi tekući ekran** (`App.switchScale`: prethodno se skine stari ekran, pa se menja stylesheet — inače bi se
   ceo stari stablo stilizovalo za ništa). Tajmer se ne restartuje dok teče, da bi interfejs pratio prevlačenje prozora
   korak po korak (~2 % po skoku), a ne tek pošto se prevlačenje završi.
5. **Novi ekran je sređen pre prvog kadra:** `App.handleScreenTransition` završava sa `ResponsiveHost.settleNow()`
   (`SETTLE_PASSES` krugova CSS-a, rasporeda i `PostLayoutTask.flushPending()` u istom događaju), a `adoptWantedScale()`
   prelazi na skalu koju izmereni ekran stvarno traži (do `MAX_SCALE_SWITCHES` puta). Isto sređivanje se izvršava posle
   svakog ciklusa rasporeda (`settleInPulse`), što pokriva lenjo građen tab, čija se prva slika inače iscrtavala u
   nekoliko kadrova.
6. `App.keepWindowMinimumInSync` drži minimum prozora jednakim minimumu ekrana pri `MIN_SCALE` (plus dekoracije
   prozora, uz gornju granicu veličine ekrana).

Merenja koja se u projektnoj dokumentaciji (`CLAUDE.md`) navode za ovu petlju (ne mogu se ponoviti iz repozitorijuma,
jer probni harnes nije sačuvan): ponovno građenje radnog prostora ~40–60 ms (topao JVM, CSS i raspored uključeni);
simulirano vučenje prozora 1920 ↔ 1100 px sa novim vrednostima daje 14–17 skokova od ~0.02–0.026, sa konačnom skalom
u roku od ~10–50 ms po otpuštanju, dok je raniji tok „čekaj da prestane" prikazivao pogrešnu skalu 0.7–1.7 s i skakao
sa 1.00 na 0.65 odjednom.

### 8.4 Građenje bez curenja

Pošto se ekran iznova gradi, `SimulationView.dispose()` uklanja sve slušaoce sa dugovečnih ViewModel-a i
poziva `dispose()` na svakom tab-ViewModel-u. Ono što ViewModel nadživljava mora da se vrati: izabrani tab
(`selectedComponentProperty`), istaknuta instrukcija i pozicija dnevnika (primenjuju se preko `PostLayoutTask`).
Ništa u računanju minimalne/željene veličine ne sme prolaziti kroz podstablo: `Node.lookup(selector)` ponovo parsira
selektor u svakom čvoru (i `TabPaneSkin` stavlja sadržaj *ispred* trake sa tabovima), pa je traženje `.tab-header-area`
preko `lookup` usporavalo promenu veličine — zato se traka nalazi pregledom direktne dece.

### 8.5 Šta ostaje na projektnoj veličini

Bočne trake zadržavaju projektne proporcije (dve liste imaju **fiksne** visine); razdelnici se ponovo postavljaju pri
svakoj promeni širine (v. 6.1).

### 8.6 Prozori-inspektori ne skaliraju sa aplikacijom

Inspektor je zaseban prozor, a ne deo ekrana; pri skali < 1.0 njegov tekst bi bio nečitljiv. `InspectorWindows.build`
izvršava graditelj u `UiScale.atScale(max(1.0, faktor), …)`: sve što graditelj traži od `UiScale` (dužine, fontove,
širine kolona, stylesheet) dobija sopstvenu skalu, koju scena pamti, pa naknadna promena teme restilizuje prozor na istoj
skali. Skala važi **samo tokom** `build()`, pa se skalirane dužine inspektora računaju tamo (konstante u projektnoj
veličini skaliraju se sa `UiScale.px` pri upotrebi, ne u inicijalizaciji polja).

---

## 9. Teme (svetla / tamna)

Boja je korisničko podešavanje (`AppTheme { LIGHT, DARK }`, ekran *Settings*, `AppSettings`, fajl
`~/.vmsimulator/settings.properties`, ključ `theme`; fajl se čita iznova pri svakom pristupu da čuvanje jednog podešavanja
nikad ne odbaci drugo koje je napisala novija verzija). Za svaku temu postoji po jedna datoteka
(`light-theme.css`, `dark-theme.css`).

- **Obe datoteke definišu tačno iste klase, istim redom, sa istim veličinama, razmacima i fontovima; razlikuju se samo boje.**
  To promenu teme čini čistim **ponovnim stilizovanjem** — `App.changeTheme` predaje scenu svakog otvorenog `Stage`-a
  (uključujući inspektore) `UiScale.changeTheme`, bez ponovnog građenja ekrana. Kad bi tamna tema imala drugu veličinu,
  merenja u kodu (`WidthCalculator`, projektne veličine) prestala bi da odgovaraju onome što se crta.
- **Nijedna boja u Java kodu** (`Color.web`, `setFill`, `setStyle`): čvor dobija stilsku klasu, a boja se zadaje u obe teme.
- Ugrađene kontrole koje aplikacija ne stilizuje (TabPane, SplitPane, ScrollPane, iskačuća lista ComboBox-a) su
  Modena iz JavaFX-a, koja je svetla; `.root` u `dark-theme.css` ponovo izvodi njene promenljive palete
  (`-fx-base`, `-fx-background`, `-fx-control-inner-background`, `-fx-text-background-color`). Dve zamke Modene za tamnu
  osnovu: boje izvedene kao *tamnije* nijanse pozadine polja (tekst-primer i selekcija u `TextField`-u), i kontrola čije
  se stilske klase zamenjuju (`getStyleClass().setAll(...)`, kao u `ConfigEditorWindowSupport`) gubi Modenu boju teksta,
  pa njeno pravilo mora sâmo postaviti `-fx-text-fill`.
- Prefiksi klasa po komponenti (`data-table-*`, `field-box-*`, `schematic-*` ...); nikad se ne koristi ime ugrađene
  JavaFX klase za sopstvenu.

---

## 10. Obrasci projektovanja i načela — pregled

| Obrazac / načelo | Gde | Zašto |
|---|---|---|
| MVVM sa jednosmernom zavisnošću | ceo projekat | model nezavisan od GUI-ja; svaki deo se može proveriti odvojeno |
| Observer (JavaFX svojstva, `ObservableList`) | ViewModel ↔ View | pogled se vezuje, ne prozivа |
| Mediator | `AppViewModel` + `*NavigationListener` | ViewModel-i ne znaju jedni za druge, samo za „šta smem da tražim" |
| Command (`execute`/`undo`) | `SimulationStep` | kretanje unapred i unazad kroz korake |
| Automat stanja | svaki korak vraća sledeći | tok zavisi od stanja (TLB pogodak/promašaj, page fault, prljavost) |
| Template Method | `TLBLookupStep`, `TLBUpdateStep`, `TLBEvictionStep`, `MemoryAccessStep` | opšti tok korakâ, specifičnosti straničenja u podklasi |
| Strategy | `EvictionPolicy`, `TLB` (3 organizacije) | zamenljive politike i strukture |
| Factory | `SimulationFactory` | izbor konteksta prema tipu prevođenja |
| Retke strukture / prozori | `Memory`, `Disk`, `PageTable`, `WindowedTableView` | veličine skalirane širinom adrese |
| „Čist podatak" opisa | `StepDescription` (`record`) + `ResourceBundle` | model nema pojma o lokalizaciji |
| „Ništa tvrdo kodirano" | ceo View | svaka mera se izvodi iz svojstava (`bind`), `UiScale.px` ili CSS-a |
| Bez transformacije skale | `UiScale`, `ThemeCss`, `ResponsiveHost` | oštar tekst pri svakoj skali |

Projektno pravilo „bez tvrdo kodiranih vrednosti" (iz `CLAUDE.md`): raspored, dimenzije, boje, tekstovi i brojevi
koji mogu da se promene izvode se iz svojstava i povezivanjem (`bind`, `Bindings`, `map/when`), a ne čitanjem
vrednosti jednom i dodelom; veličine prate izvor (roditelj, metrika fonta, dužina sadržaja); vrednosti iz
konfiguracije simulacije (širina adrese, geometrija TLB-a) teku od modela kroz ViewModel do pogleda, ne kopiraju se u
lokalne konstante; istinski fiksne konstante idu na jedno imenovano mesto ili u CSS.

---

## 11. Primer: tok jednog scenarija kroz sistem

Konfiguracija `config/showcase.toml`: `pageBits = 3`, `wordBits = 6`, `physicalAddressBits = 8`,
`addressableUnit = 4`, 2 korisnika, direktno preslikan TLB sa 8 unosa.

Izvedene veličine: virtuelna adresa 9 bita; okvira `2^(8−6) = 4`; deskriptor `2 + 2 + 32 = 36` bita → 2 jedinice
(po 32 bita); tablica jednog korisnika `2 · 8 = 16` jedinica < stranica od 64 → po **1 zaključan okvir** po korisniku,
ostaju **2 slobodna okvira** za korisničke stranice.

Pet instrukcija (RD u0 str.1 — WR u0 str.1 — RD u0 str.2 — RD u1 str.1 — EX u1 str.1) izvršava se u **38 koraka**
(uhvaćeno pokretanjem modela bez GUI-ja; nazivi su `StepDescriptionKey` vrednosti):

| Instrukcija | Koraci | Šta se događa |
|---|---|---|
| [0] RD u0 str.1 | 1–9 | `INSTRUCTION_FETCHED` → `TLB_LOOKUP_MISS` → `PAGE_TABLE_ADDRESS_FORMED` → `PAGE_TABLE_LOOKUP_FAULT` → `PAGE_FAULT_LOADING` → `PAGE_LOADED_INTO_MEMORY` → `PHYSICAL_ADDRESS_FROM_PAGE_TABLE` → `TLB_INSERTED` → `MEMORY_READ` |
| [1] WR u0 str.1 | 10–14 | `INSTRUCTION_FETCHED` → `TLB_LOOKUP_HIT` → `TLB_DIRTY_BIT_UPDATED` → `PHYSICAL_ADDRESS_FROM_TLB` → `MEMORY_WRITTEN` |
| [2] RD u0 str.2 | 15–23 | isto kao [0], u poslednji slobodni okvir; memorija je sada puna |
| [3] RD u1 str.1 | 24–34 | ... `PAGE_TABLE_LOOKUP_FAULT` → `PAGE_FAULT_NO_FRAME` → `FRAME_EVICTED_DIRTY` → `PAGE_STORED_TO_DISK` → `PAGE_LOADED_INTO_MEMORY` → ... → `MEMORY_READ` |
| [4] EX u1 str.1 | 35–38 | `INSTRUCTION_FETCHED` → `TLB_LOOKUP_HIT` → `PHYSICAL_ADDRESS_FROM_TLB` → `MEMORY_EXECUTED` |

Korak 12 (`TLB_DIRTY_BIT_UPDATED`) pokazuje ključnu osobinu modela: D bit se postavlja samo u TLB unosu, a deskriptor u
tablici ostaje čist. U koraku 29 (`FRAME_EVICTED_DIRTY`) izbacivanje **preuzima** D bit iz TLB unosa u deskriptor pre
odluke o povratnom upisu, a taj isti korak poništava TLB unos žrtve — zato direktno preslikani TLB u koraku 33 nema
potrebe za posebnim `PageTLBEvictionStep`. Kraj scenarija: disk blok stranice user0/page1 sadrži `0x1234ABCD` na ofsetu
21 (0x15) — povratni upis u koraku 30 je sačuvao izmenu.

Šta korisnik vidi za, na primer, korak 4 (`PAGE_TABLE_LOOKUP_FAULT`): u desnoj traci se prikazuje tekst iz
`StepDescriptions.properties` („Page table lookup: entry 1 of user 0's page table is not valid (page fault)."); na MMU
tabu se pale žice `PAGE_TO_OFFSET`, `ZERO_FILL_TO_OFFSET`, `OFFSET_TO_ADDER`, `POINTER_TO_ADDER`, `ADDER_TO_TABLE`,
`ADDER_TO_ROW` (jer je istorija korakâ od poslednjeg fetch-a prošla kroz `FormPageTableAddressStep` i
`PageTableLookupStep`; prvih pet žica pali `FormPageTableAddressStep`, poslednju `PageTableLookupStep`). Bedž na tabu OS
pojaviće se tek u sledećem koraku (`PageFaultStep`), jer bedž zavisi samo od komponenti koje je zahvatio *poslednji*
izvršeni korak.

---

## 12. Ograničenja i pravci daljeg razvoja

- Nije implementirano segmentiranje ni segmentirano straničenje (`SimulationFactory` bi vratio `null` kontekst;
  `validate()` to sprečava). `TLBEntry` i apstraktne klase koraka su za to pripremljene.
- Samo FIFO politika zamene; nema LRU/clock/optimalne. Apstrakcija `EvictionPolicy` je spremna, ali se u
  `PageSimulationContext.init()` politika ne bira iz konfiguracije nego je tvrdo `new FIFOEvictionPolicy()`.
- Klasa `OS` i `TLBViewModel` su prazne; `primary.fxml`/`secondary.fxml` i njihovi kontroleri su ostatak šablona.
- Nema automatskih testova. Provera se svodi na ručno pokretanje i na TOML scenarije u `config/`
  (`showcase*.toml` za demonstraciju, `os_test`, `maxframes_test`, `tlb_eviction_writeback_test` za pojedine tokove,
  `validation_*.toml` za negativne slučajeve validacije). Direktorijum `config/` je u `.gitignore`, znači da scenariji **nisu**
  u verzionoj kontroli.
- Tekstovi interfejsa (naslovi, dugmad, poruke) su napisani direktno u kodu (engleski); jedino opisi koraka su
  izdvojeni u `ResourceBundle`.
- Adresa na disku je široka 32 bita (`diskBits`, polje `Disk` u deskriptoru), pa simulator podržava najviše `2^32` stranica svih
  korisnika zajedno (`pageBits + log2(numberOfUsers) ≤ 32`), iako širina virtuelne adrese sme do 62 bita. Šire adresne prostore
  `validate()` odbija; da bi se podržali, trebalo bi proširiti polje `Disk` (a s njim veličinu deskriptora i Fajstelovu mrežu).

---

## 13. Napomene za proveru pre citiranja u radu

1. **Ispravljeno: povratni upis (`PageStoreToDiskStep`) čitao je pogrešan opseg memorije.** Ranije je
   `memory.readBlock(frame, pageSize)` prosleđivao **broj okvira** kao adresu (umesto `frame << wordBits`, kako to radi
   `PageLoadIntoMemoryStep`), pa je na disk stizao prazan blok. Provera (pokretanje modela bez GUI-ja): posle `showcase.toml`
   disk blok stranice user0/page1 sada ima `0x1234ABCD` na ofsetu 21 (0x15); scenario „upis → izbacivanje → povratni upis →
   ponovno učitavanje" (probni fajl van repozitorijuma, 4 instrukcije, 43 koraka) vraća upisanu vrednost pri čitanju.
2. **Ispravljeno: upis koji izazove page fault sada postavlja D bit.** Ranije je tok iz `PageLoadIntoMemoryStep` išao
   direktno u `FormPhysicalAddressFromPageTableStep`, pa ni deskriptor ni novi TLB unos nisu postajali prljavi, a kasnije
   izbacivanje te stranice bi izgubilo upis. Sada `PageLoadIntoMemoryStep` vraća `PageTableUpdateDirtyBitStep` kada je
   instrukcija upis a deskriptor čist (isti uslov kao u `PageTableLookupStep`). Posledica: niz koraka za upis na nedostajuću
   stranicu duži je za jedan korak (`PAGE_TABLE_DIRTY_BIT_UPDATED` između `PAGE_LOADED_INTO_MEMORY` i
   `PHYSICAL_ADDRESS_FROM_PAGE_TABLE`); brojevi koraka koje ste eventualno već zabeležili za takve scenarije više ne važe
   (za `showcase.toml` ostaje 38, jer nema upisa na nedostajuću stranicu). `MemoryTabViewModel` je prilagođen da i u
   tom dodatnom koraku prepozna „stranica upravo učitana" (izmena nije proveravana u pokrenutom interfejsu). Provera:
   deskriptor i TLB unos imaju `dirty=true`; poništavanje svih koraka vraća početno stanje, a ponovno izvršavanje daje isti
   niz koraka i isto stanje, na svim konfiguracijama iz `config/` osim namerno nepotpunog `sim_config.toml` (bez `tlbSize`).
3. **Ispravljeno: seme adresa na disku više ne zavisi od identitetskog heša.** `generateDiskSeed()` je ranije prosleđivao enum
   `translationType` u `Objects.hash`, a `Enum.hashCode()` JVM ne čuva isti između pokretanja. Sada se koristi `name()`, pa je
   seme čista funkcija konfiguracije. Provera: pokretanje sa `-XX:hashCode=2` (svi identitetski heševi jednaki) daje isto
   seme kao podrazumevani režim, dok bi stara formula dala različite vrednosti (−774 757 562 naspram 921 762 440 za
   `showcase.toml`). Posledica: adrese na disku su sada druge nego pre ove izmene (npr. na ranijim slikama ekrana), ali su
   ponovljive; ako ih citirate u radu, uzmite nove vrednosti.
4. **Ispravljeno: TLB ključ i disk blokovi za veoma velike adresne prostore.** `TLB.calculateTag` je pomerao `int` ID korisnika
   (`processId << addressBits`), pa je za `pageBits ≥ 32` ID upadao u bitove stranice: sa `pageBits = 32` i dva korisnika
   ključevi (korisnik 1, stranica 0) i (korisnik 0, stranica 1) bili su isti (`0x1`) i pristup dobijao **lažni TLB pogodak**
   (korisnik 1 čita preko okvira korisnika 0); od 31 bita nadalje ID se i znakom proširivao. Sada se pomera `long`
   (`(long) processId << addressBits`). Zasebno, `DiskAddressGenerator` radi `(ulaz + seme) mod 2^32`, pa bi za više od `2^32`
   stranica dve stranice dobile isti disk blok; `validate()` sada odbija takve konfiguracije (`pageBits + log2(numberOfUsers) ≤ 32`,
   poruka „… exceeds the 32-bit disk address …", fajl `config/validation_disk_capacity.toml`). Provera: konfiguracija sa
   `pageBits = 32` se odbija; granični slučaj `pageBits = 31` sa dva korisnika daje ključ `0x80000000` (ranije
   `0xffffffff80000000`), tačan promašaj za korisnika 1 i tačan pogodak pri ponovnom pristupu; nijedna postojeća konfiguracija
   nije pogođena novim pravilom (najveća je `showcase_large_params.toml` sa `pageBits = 28`).
5. **`CLAUDE.md` i kod se razlikuju:** dokument navodi da je `PageOSMemoryManager.allocatedFrames` `HashMap`, a u kodu je `TreeMap`
   (sortiran, radi šetnje kroz okvire u rastućem redosledu). Ovaj dokument prati kod.
6. **Izvori van koda:** merenja u 8.3 i pravila iz poglavlja 10 potiču iz `CLAUDE.md`, ne iz koda; merenja se ne mogu
   ponoviti iz repozitorijuma.
7. **Jedina zavisnost `viewmodel → view`** je `ValueConverter` (v. 3); ako se u radu tvrdi „striktna" slojevitost,
   navesti to odstupanje ili premestiti `ValueConverter` u nezavisan paket.
8. **Nekomitovane izmene:** u trenutku početka pisanja postojale su nekomitovane izmene u 15 fajlova (uključujući `Memory`,
   `PageSimulationContext`, `PageEvictionStep`, `PageLoadIntoMemoryStep`, `SimulationView`, `UiScale`, obe teme i
   `StepDescriptions.properties`); brojevi redova u poglavlju 2 odgovaraju radnom stablu, ne poslednjem komitu (`b03a920`).
   Ispravke opisane u stavkama 1–4 dodatno su izmenile `PageStoreToDiskStep`, `PageLoadIntoMemoryStep`, `MemoryTabViewModel`,
   `TLB`, `SimulationConfig` i `DiskAddressGenerator`, tako da se ni ti brojevi više ne poklapaju tačno.
9. **Slab test u `config/`:** `validation_addressable_unit.toml` postavlja samo `addressableUnit = 16`, pa validacija pada već na
   prvoj proveri („physicalAddressBits is required") i pravilo o adresibilnoj jedinici se tim fajlom zapravo ne proverava.

---

## Prilog A — struktura paketa

```
rs.ac.bg.etf
├── App                             ulazna tačka (Application), promena ekrana/skale/teme
├── model
│   ├── simulation                  Simulation, SimulationContext, PageSimulationContext, SimulationConfig,
│   │   │                           SimulationFactory, SimulationComponent, exceptions.InvalidConfig
│   │   └── step                    SimulationStep, StepDescription(Key) + 7 apstraktnih koraka
│   │       └── page                15 konkretnih koraka straničenja
│   ├── tlb                         TLB, TLBEntry, AssociativeTLB, DirectTLB, SetAssociativeTLB
│   ├── os                          OS (prazna), OSMemoryManager, PageOSMemoryManager, EvictionPolicy, FIFOEvictionPolicy
│   ├── table                       PageTable, PageTableDescriptor
│   ├── memory                      Memory, Instruction, exceptions.MemoryBoundsException
│   ├── disk                        Disk, DiskAddressGenerator
│   └── settings                    AppSettings, AppTheme
├── viewmodel
│   ├── AppViewModel, ApplicationScreenState, MainMenuViewModel, ConfigurationViewModel, SettingsViewModel
│   ├── SimulationViewModel
│   ├── PagedMMUTabViewModel, PagedTLBTabViewModel, PagedOSTabViewModel, MemoryTabViewModel, (TLBViewModel — prazna)
│   ├── InstructionEntry, PageTableEntry, MemoryInitEntry        modeli redova u editorima konfiguracije
│   └── listeners                   4 interfejsa za navigaciju
└── view
    ├── MainMenuView, ConfigurationView, SettingsView, SimulationView, PagedMMUTabView, PagedOSTabView, MemoryTabView, PageTableView
    ├── config                      InstructionsEditorWindow, PageTablesEditorWindow, MemoryInitEditorWindow, ConfigEditorWindowSupport, EditorColumn
    ├── inspector                   PageTable / TLB / Memory / DiskBlock inspektori + *RowSource + *Row
    ├── memory                      MemoryTableView
    ├── os                          FrameTableView, ReplacementQueueView, ReplacementQueueInspectorWindow, UserSummaryView
    ├── shape                       BitWidthLine, CurlyBrace
    ├── tlb                         PagedTLBTabView, TLBBodyView, Associative/Direct/SetAssociativeTLBView, PagedTLBRowView, TLBRowView
    └── util                        UiScale, ThemeCss, ResponsiveHost, ResponsiveLayout, PostLayoutTask, SchematicTab, SchematicCanvas,
                                    WindowedTableView/-Column/-RowSource, AddressScaleScrollBar, WidthCalculator, FieldBoxes,
                                    InspectorWindows, StepDescriptionFormatter, ValueConverter, HexLongConverter, BackButton, LockedRow, RowTooltip
resources/rs/ac/bg/etf: light-theme.css, dark-theme.css, fonts.css, fonts/*.ttf, i18n/StepDescriptions.properties, primary/secondary.fxml (neaktivni)
```

## Prilog B — rečnik pojmova (srpski ↔ identifikator u kodu)

| Srpski | Engleski / u kodu |
|---|---|
| straničenje | *paging*, `PAGED` |
| virtuelna / fizička adresa | *virtual / physical address*, VA / PA |
| stranica; okvir (blok) | *page*; *frame* / *block* |
| ofset unutar stranice („reč") | *word*, `wordBits` |
| tablica stranica; deskriptor (ulaz) | *page table*, `PageTable`; *descriptor*, `PageTableDescriptor` |
| pokazivač na tablicu stranica | *page table pointer*, PTP, `getCurrentPTP()` |
| bit validnosti (V), bit izmene (D, *dirty*) | `valid`, `dirty` |
| TLB (bafer za prevođenje adresa) | *Translation Lookaside Buffer*, `TLB` |
| pogodak / promašaj u TLB-u | *TLB hit / miss* |
| potpuno asocijativni / direktno preslikani / skupovno asocijativni | `ASSOCIATIVE` / `DIRECT` / `SET_ASSOCIATIVE` |
| promašaj stranice | *page fault*, `PageFaultStep` |
| izbacivanje (zamena) stranice; žrtva | *eviction*, `PageEvictionStep`; *victim* |
| povratni upis (write-back) | `PageStoreToDiskStep`, `PageTLBEvictionStep.writebackDirty()` |
| upravljač memorijom (MMU) | *Memory Management Unit* |
| adresibilna jedinica | *addressable unit*, `addressableUnit` |
| korak simulacije / kontekst simulacije | `SimulationStep` / `SimulationContext` |

## Prilog C — parametri konfiguracije (TOML)

| Ključ | Tip | Podrazumevano | Napomena |
|---|---|---|---|
| `translationType` | `paged` \| `segmented` \| `segmented_paged` | `paged` | samo `paged` se prihvata |
| `tlbType` | `associative` \| `direct` \| `set_associative` | `associative` | |
| `addressableUnit` | int | 1 | 1, 2, 4, 8 (bajtova) |
| `physicalAddressBits` | int | (obavezno) | |
| `numberOfUsers` | int | (obavezno) | stepen dvojke |
| `wordBits`, `pageBits` | int | (obavezno) | `segmentBits` postoji, ali se za `paged` ne koristi |
| `tlbSize` | int | (obavezno) | stepen dvojke |
| `tlbEntriesPerSet` | int | (obavezno za `set_associative`) | stepen dvojke, `≤ tlbSize` |
| `[[instructions]]` | `user`, `accessType` (`RD`/`WR`/`EX`), `virtualAddress`, `value` (za `WR`) | prazno | |
| `[pageTables.<korisnik>.<stranica>]` | `valid`, `dirty`, `block` | prazno | nedostajući deskriptori se prave lenjo kao `valid=false` |
| `[[initialPages]]` | `userId`, `page`, `[…content]` (ofset = vrednost) | prazno | na disk ili u memoriju u zavisnosti od `valid` |

Podrazumevane vrednosti forme na ekranu konfiguracije (`ConfigurationViewModel`): `PAGED`, `ASSOCIATIVE`, `wordBits` 12,
`physicalAddressBits` 16, `pageBits` 4, `tlbSize` 16, `tlbEntriesPerSet` 2, `addressableUnit` 1, 2 korisnika.
