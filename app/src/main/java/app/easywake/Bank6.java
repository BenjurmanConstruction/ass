package app.easywake;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Trivia questions built from tables of facts. Each table is a list of "thing=answer"
 * pairs; the wrong answers for a question are taken from the other rows of the same
 * table, so they are always the same kind of thing as the right one.
 */
final class Bank6 {
    private Bank6() {}

    private static final List<String[]>[] LEVELS = make();

    @SuppressWarnings("unchecked")
    private static List<String[]>[] make() {
        List<String[]>[] l = new List[3];
        for (int i = 0; i < 3; i++) l[i] = new ArrayList<>();
        return l;
    }

    /** Level 0 is normal, 1 hard, 2 expert. */
    static String[][] trivia(int level) {
        synchronized (LEVELS) {
            if (LEVELS[0].isEmpty()) build();
        }
        return LEVELS[level].toArray(new String[0][]);
    }

    /** Turns one table into questions. The % in the template is replaced by the thing. */
    private static void table(int level, String template, String data) {
        String[] rows = data.split(";");
        List<String> answers = new ArrayList<>();
        for (String row : rows) {
            String a = row.substring(row.indexOf('=') + 1).trim();
            if (!answers.contains(a)) answers.add(a);
        }
        for (String row : rows) {
            int cut = row.indexOf('=');
            String thing = row.substring(0, cut).trim();
            String right = row.substring(cut + 1).trim();
            // The same seed every time, so a question always has the same four answers.
            Random r = new Random(thing.hashCode() * 31L + template.hashCode());
            List<String> pool = new ArrayList<>(answers);
            pool.remove(right);
            Collections.shuffle(pool, r);
            if (pool.size() < 3) continue;
            LEVELS[level].add(new String[]{template.replace("%", thing), right, pool.get(0),
                    pool.get(1), pool.get(2)});
        }
    }

    /** Questions whose answers are numbers; wrong answers are other numbers nearby. */
    private static void numbers(int level, String template, String data, int spread) {
        for (String row : data.split(";")) {
            int cut = row.indexOf('=');
            String thing = row.substring(0, cut).trim();
            int right = Integer.parseInt(row.substring(cut + 1).trim());
            Random r = new Random(thing.hashCode() * 17L + 5);
            List<Integer> wrong = new ArrayList<>();
            int guard = 0;
            while (wrong.size() < 3 && guard++ < 200) {
                int off = 1 + r.nextInt(spread);
                int cand = right + (r.nextBoolean() ? off : -off);
                if (cand > 0 && cand != right && !wrong.contains(cand)) wrong.add(cand);
            }
            if (wrong.size() < 3) continue;
            LEVELS[level].add(new String[]{template.replace("%", thing), String.valueOf(right),
                    String.valueOf(wrong.get(0)), String.valueOf(wrong.get(1)),
                    String.valueOf(wrong.get(2))});
        }
    }

    private static String roman(int n) {
        int[] v = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] s = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < v.length; i++) {
            while (n >= v[i]) {
                sb.append(s[i]);
                n -= v[i];
            }
        }
        return sb.toString();
    }

    private static void build() {
        // ----- world capitals -----
        table(0, "What is the capital of %?", "France=Paris;England=London;Russia=Moscow;"
                + "China=Beijing;India=New Delhi;Greece=Athens;Cuba=Havana;Peru=Lima;"
                + "Kenya=Nairobi;Thailand=Bangkok;Poland=Warsaw;Sweden=Stockholm;"
                + "Austria=Vienna;Belgium=Brussels;the Netherlands=Amsterdam;"
                + "Denmark=Copenhagen;Hungary=Budapest;Scotland=Edinburgh;Iraq=Baghdad;"
                + "Iran=Tehran;Lebanon=Beirut;Chile=Santiago;"
                + "Colombia=Bogota;Venezuela=Caracas;Jamaica=Kingston;Vietnam=Hanoi;"
                + "the Philippines=Manila;Indonesia=Jakarta;Saudi Arabia=Riyadh;"
                + "Ukraine=Kyiv;Finland=Helsinki;Iceland=Reykjavik;Czech Republic=Prague;"
                + "Romania=Bucharest;Afghanistan=Kabul;Syria=Damascus;Taiwan=Taipei");
        table(1, "What is the capital of %?", "Morocco=Rabat;Ghana=Accra;Ethiopia=Addis Ababa;"
                + "Pakistan=Islamabad;Bangladesh=Dhaka;Nepal=Kathmandu;"
                + "Malaysia=Kuala Lumpur;Cambodia=Phnom Penh;Mongolia=Ulaanbaatar;"
                + "Uruguay=Montevideo;Paraguay=Asuncion;Ecuador=Quito;"
                + "Costa Rica=San Jose;Panama=Panama City;Croatia=Zagreb;Serbia=Belgrade;"
                + "Bulgaria=Sofia;Slovakia=Bratislava;Estonia=Tallinn;Latvia=Riga;"
                + "Lithuania=Vilnius;Jordan=Amman;Qatar=Doha;Kuwait=Kuwait City;"
                + "Senegal=Dakar;Uganda=Kampala;Zimbabwe=Harare;Tunisia=Tunis;"
                + "Algeria=Algiers;Libya=Tripoli;Sudan=Khartoum;Angola=Luanda;"
                + "Tanzania=Dodoma;North Korea=Pyongyang;Belarus=Minsk;Georgia=Tbilisi");
        table(2, "What is the capital of %?", "Uzbekistan=Tashkent;Kyrgyzstan=Bishkek;"
                + "Tajikistan=Dushanbe;Turkmenistan=Ashgabat;Azerbaijan=Baku;"
                + "Armenia=Yerevan;Laos=Vientiane;Bhutan=Thimphu;Brunei=Bandar Seri Begawan;"
                + "Madagascar=Antananarivo;Mozambique=Maputo;Zambia=Lusaka;Malawi=Lilongwe;"
                + "Botswana=Gaborone;Namibia=Windhoek;Mali=Bamako;Niger=Niamey;"
                + "Burkina Faso=Ouagadougou;Ivory Coast=Yamoussoukro;Cameroon=Yaounde;"
                + "Rwanda=Kigali;Slovenia=Ljubljana;Moldova=Chisinau;Albania=Tirana;"
                + "North Macedonia=Skopje;Montenegro=Podgorica;Honduras=Tegucigalpa;"
                + "Nicaragua=Managua;Suriname=Paramaribo;Fiji=Suva;Oman=Muscat;"
                + "Yemen=Sanaa;Liechtenstein=Vaduz;Malta=Valletta;Cyprus=Nicosia");

        // ----- US states -----
        table(0, "What is the capital of the state of %?", "Georgia=Atlanta;"
                + "Massachusetts=Boston;Colorado=Denver;Arizona=Phoenix;Hawaii=Honolulu;"
                + "Tennessee=Nashville;Ohio=Columbus;Indiana=Indianapolis;Utah=Salt Lake City;"
                + "Oklahoma=Oklahoma City;Idaho=Boise;Iowa=Des Moines;Arkansas=Little Rock;"
                + "Mississippi=Jackson;Virginia=Richmond;Wisconsin=Madison");
        table(1, "What is the capital of the state of %?", "Alabama=Montgomery;"
                + "Alaska=Juneau;Connecticut=Hartford;Delaware=Dover;Kansas=Topeka;"
                + "Kentucky=Frankfort;Louisiana=Baton Rouge;Maine=Augusta;Maryland=Annapolis;"
                + "Michigan=Lansing;Minnesota=Saint Paul;Missouri=Jefferson City;"
                + "Montana=Helena;Nebraska=Lincoln;New Hampshire=Concord;New Jersey=Trenton;"
                + "New Mexico=Santa Fe;North Carolina=Raleigh;North Dakota=Bismarck;"
                + "Oregon=Salem;Rhode Island=Providence;South Carolina=Columbia;"
                + "South Dakota=Pierre;Vermont=Montpelier;West Virginia=Charleston;"
                + "Wyoming=Cheyenne");
        table(0, "Which state is nicknamed the % State?", "Lone Star=Texas;"
                + "Golden=California;Empire=New York;Grand Canyon=Arizona;Peach=Georgia;"
                + "Aloha=Hawaii;Garden=New Jersey;Bluegrass=Kentucky;Volunteer=Tennessee;"
                + "Buckeye=Ohio;Hoosier=Indiana;Silver=Nevada;Evergreen=Washington;"
                + "Bay=Massachusetts;Sooner=Oklahoma;Cornhusker=Nebraska;"
                + "Mount Rushmore=South Dakota;Pine Tree=Maine;Tar Heel=North Carolina");
        table(1, "Which state is nicknamed the % State?", "Badger=Wisconsin;"
                + "Show-Me=Missouri;Beehive=Utah;Centennial=Colorado;Keystone=Pennsylvania;"
                + "Pelican=Louisiana;Magnolia=Mississippi;Hawkeye=Iowa;Gem=Idaho;"
                + "Treasure=Montana;Green Mountain=Vermont;Granite=New Hampshire;"
                + "Ocean=Rhode Island;First=Delaware;Old Line=Maryland;"
                + "Palmetto=South Carolina;Natural=Arkansas;Sunflower=Kansas;"
                + "North Star=Minnesota;Wolverine=Michigan;Prairie=Illinois;"
                + "Mountain=West Virginia;Constitution=Connecticut;Cowboy=Wyoming;"
                + "Peace Garden=North Dakota;Beaver=Oregon;Yellowhammer=Alabama;"
                + "Last Frontier=Alaska");
        table(0, "Which US state is the city of % in?", "Chicago=Illinois;Miami=Florida;"
                + "Houston=Texas;Seattle=Washington;Las Vegas=Nevada;Detroit=Michigan;"
                + "New Orleans=Louisiana;Philadelphia=Pennsylvania;Memphis=Tennessee;"
                + "Baltimore=Maryland;Milwaukee=Wisconsin;Cleveland=Ohio;"
                + "Minneapolis=Minnesota;St. Louis=Missouri;Louisville=Kentucky;"
                + "Anchorage=Alaska;Albuquerque=New Mexico;Omaha=Nebraska;"
                + "Charlotte=North Carolina;Newark=New Jersey;Tulsa=Oklahoma;"
                + "Birmingham=Alabama;Savannah=Georgia;Tucson=Arizona;San Diego=California;"
                + "Buffalo=New York;Aspen=Colorado;Fargo=North Dakota;Wichita=Kansas;"
                + "Cheyenne=Wyoming;Sioux Falls=South Dakota;Burlington=Vermont");

        // ----- cities and landmarks -----
        table(0, "Which country is the city of % in?", "Barcelona=Spain;Munich=Germany;"
                + "Venice=Italy;Sydney=Australia;Toronto=Canada;Mumbai=India;"
                + "Shanghai=China;Osaka=Japan;Rio de Janeiro=Brazil;Istanbul=Turkey;"
                + "Cape Town=South Africa;Dubai=United Arab Emirates;Marseille=France;"
                + "Liverpool=England;Glasgow=Scotland;St. Petersburg=Russia;"
                + "Casablanca=Morocco;Alexandria=Egypt;Auckland=New Zealand;"
                + "Geneva=Switzerland;Krakow=Poland;Cancun=Mexico;Medellin=Colombia;"
                + "Lagos=Nigeria;Karachi=Pakistan;Ho Chi Minh City=Vietnam;"
                + "Antwerp=Belgium;Rotterdam=the Netherlands;Salzburg=Austria;"
                + "Porto=Portugal;Gothenburg=Sweden;Busan=South Korea;Cusco=Peru");
        table(1, "In which country would you find %?", "the Taj Mahal=India;"
                + "Machu Picchu=Peru;Stonehenge=England;Angkor Wat=Cambodia;"
                + "the Sydney Opera House=Australia;Mount Fuji=Japan;"
                + "Mount Kilimanjaro=Tanzania;the Acropolis=Greece;Chichen Itza=Mexico;"
                + "the Alhambra=Spain;Neuschwanstein Castle=Germany;the Kremlin=Russia;"
                + "Table Mountain=South Africa;Ha Long Bay=Vietnam;"
                + "the Burj Khalifa=United Arab Emirates;Loch Ness=Scotland;"
                + "Banff National Park=Canada;the Forbidden City=China;"
                + "the Galapagos Islands=Ecuador;Easter Island=Chile;Borobudur=Indonesia;"
                + "the Leaning Tower of Pisa=Italy;the Blue Mosque=Turkey;"
                + "the Eiffel Tower=France;the statue of Christ the Redeemer=Brazil;"
                + "the Great Sphinx=Egypt;Uluru=Australia;the Cliffs of Moher=Ireland;"
                + "the Atomium=Belgium;Lake Bled=Slovenia");
        table(1, "What is the largest city in the state of %?", "Illinois=Chicago;"
                + "Texas=Houston;California=Los Angeles;Pennsylvania=Philadelphia;"
                + "Michigan=Detroit;Washington=Seattle;Maryland=Baltimore;"
                + "Wisconsin=Milwaukee;Nevada=Las Vegas;Louisiana=New Orleans;"
                + "Missouri=Kansas City;Minnesota=Minneapolis;Kentucky=Louisville;"
                + "Nebraska=Omaha;New Mexico=Albuquerque;Alaska=Anchorage;"
                + "Florida=Jacksonville;North Carolina=Charlotte;Kansas=Wichita;"
                + "Virginia=Virginia Beach;South Carolina=Charleston;Oregon=Portland;"
                + "New Jersey=Newark;Vermont=Burlington;"
                + "Delaware=Wilmington;Montana=Billings;North Dakota=Fargo;"
                + "South Dakota=Sioux Falls");
        table(1, "What is the main language spoken in %?", "Brazil=Portuguese;"
                + "Mexico=Spanish;Egypt=Arabic;Austria=German;Iran=Persian;"
                + "Israel=Hebrew;Greece=Greek;Thailand=Thai;Vietnam=Vietnamese;"
                + "Turkey=Turkish;Poland=Polish;the Netherlands=Dutch;Denmark=Danish;"
                + "Finland=Finnish;Hungary=Hungarian;Japan=Japanese;South Korea=Korean;"
                + "Russia=Russian;Ethiopia=Amharic;Bangladesh=Bengali;Cambodia=Khmer;"
                + "Kenya=Swahili;Pakistan=Urdu;Sweden=Swedish;Italy=Italian");
        table(1, "What is the currency of %?", "Japan=Yen;the United Kingdom=Pound sterling;"
                + "India=Rupee;China=Yuan;Russia=Ruble;South Korea=Won;"
                + "Switzerland=Swiss franc;Brazil=Real;South Africa=Rand;Turkey=Lira;"
                + "Israel=Shekel;Thailand=Baht;Poland=Zloty;Sweden=Krona;Vietnam=Dong;"
                + "Saudi Arabia=Riyal;Indonesia=Rupiah;Malaysia=Ringgit;Hungary=Forint;"
                + "the United Arab Emirates=Dirham;Nigeria=Naira;Peru=Sol;"
                + "Ukraine=Hryvnia;Germany=Euro;Mexico=Mexican peso");

        // ----- science -----
        table(0, "What is the chemical symbol for %?", "oxygen=O;hydrogen=H;carbon=C;"
                + "nitrogen=N;helium=He;calcium=Ca;copper=Cu;zinc=Zn;neon=Ne;"
                + "aluminum=Al;silicon=Si;sulfur=S;chlorine=Cl;magnesium=Mg;nickel=Ni;"
                + "uranium=U");
        table(1, "What is the chemical symbol for %?", "tin=Sn;mercury=Hg;platinum=Pt;"
                + "phosphorus=P;lithium=Li;titanium=Ti;chromium=Cr;manganese=Mn;"
                + "cobalt=Co;arsenic=As;bromine=Br;iodine=I;barium=Ba;argon=Ar;"
                + "fluorine=F;boron=B;radium=Ra;plutonium=Pu");
        table(1, "Which element has the chemical symbol %?", "Fe=Iron;Au=Gold;Ag=Silver;"
                + "Na=Sodium;K=Potassium;Pb=Lead;Cu=Copper;He=Helium;Ne=Neon;"
                + "Ca=Calcium;Zn=Zinc;Mg=Magnesium;Si=Silicon;Cl=Chlorine;Al=Aluminum");
        table(2, "Which element has the chemical symbol %?", "Sn=Tin;Hg=Mercury;W=Tungsten;"
                + "Pt=Platinum;Mn=Manganese;Cr=Chromium;Co=Cobalt;As=Arsenic;"
                + "Ba=Barium;Li=Lithium;Ti=Titanium;Sr=Strontium;Cs=Cesium;Kr=Krypton;"
                + "Xe=Xenon;Rn=Radon;Be=Beryllium;Ga=Gallium;Zr=Zirconium;Pd=Palladium");
        table(0, "Which planet is % from the Sun?", "first=Mercury;second=Venus;third=Earth;"
                + "fourth=Mars;fifth=Jupiter;sixth=Saturn;seventh=Uranus;eighth=Neptune");
        table(0, "What is a baby % called?", "dog=Puppy;cat=Kitten;cow=Calf;sheep=Lamb;"
                + "goat=Kid;horse=Foal;pig=Piglet;duck=Duckling;swan=Cygnet;deer=Fawn;"
                + "bear=Cub;goose=Gosling;owl=Owlet;eel=Elver;hare=Leveret");
        table(1, "What is a group of % called?", "geese on the ground=A gaggle;"
                + "bees=A swarm;whales=A pod;cattle=A herd;ants=A colony;"
                + "kittens=A litter;sheep=A flock;monkeys=A troop;oysters=A bed;"
                + "ravens=An unkindness;hippos=A bloat;jellyfish=A smack;"
                + "rhinos=A crash;ferrets=A business");
        table(1, "Who is credited with inventing or discovering %?", "the telephone="
                + "Alexander Graham Bell;the phonograph=Thomas Edison;penicillin="
                + "Alexander Fleming;the printing press with movable type=Johannes Gutenberg;"
                + "dynamite=Alfred Nobel;the polio vaccine=Jonas Salk;"
                + "the World Wide Web=Tim Berners-Lee;the cotton gin=Eli Whitney;"
                + "pasteurization=Louis Pasteur;"
                + "radium=Marie Curie;the laws of motion=Isaac Newton;"
                + "the theory of evolution by natural selection=Charles Darwin;"
                + "the assembly-line car factory=Henry Ford;vulcanized rubber="
                + "Charles Goodyear;the mechanical reaper=Cyrus McCormick;"
                + "the telegraph code of dots and dashes=Samuel Morse;"
                + "alternating current motors=Nikola Tesla;the safety elevator=Elisha Otis;"
                + "the smallpox vaccine=Edward Jenner;X-rays=Wilhelm Roentgen;"
                + "the periodic table=Dmitri Mendeleev");

        // ----- books and history -----
        table(1, "Who wrote '%'?", "Pride and Prejudice=Jane Austen;Moby-Dick=Herman Melville;"
                + "Great Expectations=Charles Dickens;The Adventures of Tom Sawyer=Mark Twain;"
                + "The Old Man and the Sea=Ernest Hemingway;The Grapes of Wrath="
                + "John Steinbeck;To Kill a Mockingbird=Harper Lee;Frankenstein=Mary Shelley;"
                + "Dracula=Bram Stoker;The Odyssey=Homer;Don Quixote=Miguel de Cervantes;"
                + "Crime and Punishment=Fyodor Dostoevsky;The Catcher in the Rye="
                + "J. D. Salinger;Brave New World=Aldous Huxley;Jane Eyre=Charlotte Bronte;"
                + "Wuthering Heights=Emily Bronte;The Hobbit=J. R. R. Tolkien;"
                + "Treasure Island=Robert Louis Stevenson;The Call of the Wild=Jack London;"
                + "Les Miserables=Victor Hugo;The Divine Comedy=Dante Alighieri;"
                + "Gulliver's Travels=Jonathan Swift;Robinson Crusoe=Daniel Defoe;"
                + "The Scarlet Letter=Nathaniel Hawthorne;Walden=Henry David Thoreau;"
                + "Fahrenheit 451=Ray Bradbury;Of Mice and Men=John Steinbeck;"
                + "A Tale of Two Cities=Charles Dickens;Hamlet=William Shakespeare;"
                + "The Raven=Edgar Allan Poe;Little Women=Louisa May Alcott;"
                + "Anna Karenina=Leo Tolstoy;The Jungle Book=Rudyard Kipling;"
                + "Alice's Adventures in Wonderland=Lewis Carroll;"
                + "The Time Machine=H. G. Wells;The Art of War=Sun Tzu;"
                + "The Prince=Niccolo Machiavelli;The Republic=Plato");
        table(1, "Who was the % president of the United States?", "2nd=John Adams;"
                + "4th=James Madison;5th=James Monroe;7th=Andrew Jackson;"
                + "16th=Abraham Lincoln;18th=Ulysses S. Grant;26th=Theodore Roosevelt;"
                + "28th=Woodrow Wilson;32nd=Franklin D. Roosevelt;33rd=Harry Truman;"
                + "34th=Dwight Eisenhower;35th=John F. Kennedy;36th=Lyndon Johnson;"
                + "37th=Richard Nixon;39th=Jimmy Carter;40th=Ronald Reagan;"
                + "41st=George H. W. Bush;42nd=Bill Clinton;43rd=George W. Bush;"
                + "44th=Barack Obama");
        numbers(1, "In what year did %?", "the American Civil War begin=1861;"
                + "the American Civil War end=1865;World War II begin=1939;"
                + "the attack on Pearl Harbor happen=1941;the Soviet Union break up=1991;"
                + "Christopher Columbus first reach the Americas=1492;"
                + "the US Constitution get signed=1787;the Titanic sink=1912;"
                + "World War I end=1918;the first airplane fly at Kitty Hawk=1903;"
                + "the Wall Street crash that began the Great Depression happen=1929;"
                + "President Kennedy die=1963;the first iPhone go on sale=2007;"
                + "the Chernobyl nuclear accident happen=1986;"
                + "the Louisiana Purchase take place=1803;Alaska become a state=1959;"
                + "the Golden Gate Bridge open=1937;the Hoover Dam get finished=1936;"
                + "the transcontinental railroad get completed in the US=1869;"
                + "the Korean War begin=1950;the US enter World War I=1917;"
                + "Martin Luther King Jr. give his 'I Have a Dream' speech=1963;"
                + "the Empire State Building open=1931;the euro coins and notes enter "
                + "circulation=2002;Hurricane Katrina hit New Orleans=2005", 12);
        numbers(2, "In what year did %?", "the Magna Carta get sealed=1215;"
                + "the Battle of Hastings take place=1066;the French Revolution begin=1789;"
                + "the Battle of Waterloo take place=1815;the Black Death reach Europe=1347;"
                + "Martin Luther post his 95 theses=1517;the Spanish Armada sail=1588;"
                + "the Pilgrims land at Plymouth=1620;the Great Fire of London happen=1666;"
                + "the Russian Revolution take place=1917;India gain independence=1947;"
                + "the Suez Canal open=1869;the Panama Canal open=1914;"
                + "the first modern Olympic Games take place=1896;"
                + "the Federal Reserve get created=1913;the Erie Canal open=1825;"
                + "the Brooklyn Bridge open=1883;the Boston Tea Party happen=1773;"
                + "the Lewis and Clark expedition set out=1804;the Alamo fall=1836", 18);

        // ----- numbers -----
        StringBuilder rom = new StringBuilder();
        for (int n : new int[]{4, 9, 14, 19, 40, 90, 400, 900, 1999, 2024, 49, 99, 444, 1066,
                1776}) {
            rom.append(rom.length() > 0 ? ";" : "").append(n).append('=').append(roman(n));
        }
        table(1, "What is the Roman numeral for %?", rom.toString());
        numbers(0, "How many % are there?", "days in a week=7;months in a year=12;"
                + "hours in a day=24;seconds in a minute=60;sides on a triangle=3;"
                + "sides on an octagon=8;sides on a pentagon=5;legs on a crab=10;"
                + "strings on a violin=4;players on a basketball team on the court=5;"
                + "players on an ice hockey team on the ice=6;"
                + "players on a volleyball team on the court=6;"
                + "players on a rugby union team on the field=15;"
                + "stripes on the US flag=13;stars on the US flag=50;"
                + "US senators=100;justices on the US Supreme Court=9;"
                + "weeks in a year=52;cards of each suit in a deck=13;"
                + "holes on a standard golf course=18;innings in a regular baseball game=9;"
                + "pockets on a pool table=6;keys on a standard piano=88;"
                + "letters in the English alphabet=26;vowels in the English alphabet=5", 6);
        numbers(1, "What is %?", "13 squared=169;14 squared=196;16 squared=256;"
                + "17 squared=289;18 squared=324;19 squared=361;25 squared=625;"
                + "3 cubed=27;4 cubed=64;5 cubed=125;12 times 12=144;15 times 15=225;"
                + "the number of feet in 100 yards=300;the number of inches in 10 feet=120;"
                + "the number of square feet in a 12 by 15 room=180;"
                + "the number of ounces in 3 pounds=48;the number of minutes in 6 hours=360",
                14);
    }
}
