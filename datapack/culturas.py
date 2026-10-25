"""Tablas de culturas de Destinos: Reinos.
Nombres INSPIRADOS en la fonética de cada región, no copiados de lugares reales ni de figuras sagradas.
Cada cultura: pre[16], mid[8], suf[16], m[12], f[12], casas[12]."""

E4 = ["", "", "", ""]

CULTURAS = {
    "norte": dict(  # nórdica
        es="nórdica", en="Nordic",
        pre="Kae Hrol Thor Vald Skar Brun Ulf Ast Grim Dur Ys Ker Hal Ran Sig Vor".split(),
        mid=E4 + "en ga ri o".split(),
        suf="vik gard heim dun mark strand fell holm rik stad borg vald nes by mor rel".split(),
        m="Ivar Ulric Hakon Torvald Sigurd Bjorn Eirik Ragnar Leif Halvar Osric Gunnar".split(),
        f="Freya Astrid Ragna Sigrid Ingrid Hilde Solveig Ylva Gudrun Thora Liv Eira".split(),
        casas="Ravenholt Dunmore Grimsby Thorne Varg Hallgrim Ulfsson Skarde Brennar Osmund Kjeld Draven".split(),
    ),
    "llanura": dict(  # ibérica / europea medieval
        es="de las llanuras", en="Lowland",
        pre="Val Cas Rob Mont Alb Sant Bel Cor Fer Leon Mir Tor Aris Vel Sal Ord".split(),
        mid=E4 + "ar el or in".split(),
        suf="mora ledo telia edo aria ona encia villa ena ia dor eira oza ano ura ante".split(),
        m="Rodrigo Sancho Pelayo Fermín Nuño Alvar Bernat Jaume Vidal Gonzalo Martín Íñigo".split(),
        f="Jimena Urraca Leonor Elvira Constanza Berenguela Sabela Inés Violante Brianda Oria Teresa".split(),
        casas="Castelar Valdés Quiroga Velasco Sarmiento Altamira Belmonte Montclar Ferrand Mirabel Lusignan Arvel".split(),
    ),
    "costa": dict(  # mediterránea
        es="mediterránea", en="Mediterranean",
        pre="Ys Lun Cal Mar Ner Sel Ael Ond Per Lir Cor Brin Amar Thal Ess Orin".split(),
        mid=E4 + "a i e o".split(),
        suf="mara porto lis rena sasa tira beth lane nia dos thea ruta line val sta lion".split(),
        m="Teodoro Nikos Lucio Dario Marco Elio Stavros Cosimo Leandro Iason Fabio Ciro".split(),
        f="Teodora Ariadna Livia Nerea Calista Selene Galatea Irene Chiara Dafne Melina Zoe".split(),
        casas="Mareni Orsini Kalanis Delmare Thalassi Corvara Lunetti Peralta Ysandre Ambrosi Nerezza Castellane".split(),
    ),
    "desierto": dict(  # árabe / persa
        es="del desierto", en="Desert",
        pre="Qal Sah Zaf Ash Dar Khal Mir Tal Jas Har Nar Az Ruk Sel Bah Iz".split(),
        mid=E4 + "ar im un ad".split(),
        suf="dir run ira esh an ar im ura ah iz oun bar eth al is ad".split(),
        m="Darius Tariq Karim Rashid Faris Kamal Yusuf Nadir Samir Idris Zafir Malik".split(),
        f="Noor Yasmin Layla Zahra Samira Amira Farah Leila Shirin Dalia Rania Soraya".split(),
        casas="Qadiri Sahrani Zafari Haruni Mirzani Kharimi Nadiri Azhari Bahrami Talesh Rukhan Darvesh".split(),
    ),
    "sabana": dict(  # África subsahariana
        es="de la sabana", en="Savanna",
        pre="Ka Mba Zan Olu Nde Ama Ki Sa Tem Kwa Ifa Bu Ndo Ase Ma Ko".split(),
        mid=E4 + "la ri ko me".split(),
        suf="ndi wa ra mbe ko si lo bari tu nga ye ji ru ma ni sa".split(),
        m="Kwame Jabari Olu Sekou Tendai Kofi Amadou Bakari Femi Juma Obi Zuberi".split(),
        f="Amara Nia Zuri Ayo Imani Folake Nala Kesi Abena Makena Adaeze Sanaa".split(),
        casas="Okara Ndiwe Kamara Diallo Adeyemi Mwangi Nkemba Sefari Obuya Tenbaro Kwasara Zambe".split(),
    ),
    "selva": dict(  # mesoamericana / caribeña
        es="de la selva", en="Jungle",
        pre="Itz Xo Tla Ce Coa Ix Pa Tez Yax Ka Chal Mo Ato Na Teo Zu".split(),
        mid=E4 + "ca li ma to".split(),
        suf="tlan pan co mal huac kan lum chi pec xca mitl lan tzin yab hua tec".split(),
        m="Tenoch Cuauhtli Yaotl Balam Ollin Mixtli Tecuani Necalli Tlacael Itzcoatl Kinich Chimal".split(),
        f="Itzel Xochitl Citlali Yaretzi Nayeli Atzi Meztli Sacnite Zyanya Tonalli Ameyal Izel".split(),
        casas="Tlacoatl Itzamal Xocotlan Coatlan Yaxkan Chalco Teomitl Ixpana Mocuil Patzin Tezcal Nahual".split(),
    ),
    "andina": dict(  # quechua / aimara
        es="andina", en="Andean",
        pre="Qori Wayra Illa Mayu Rumi Killa Sumaq Puma Yaku Tika Kusi Chaska Antu Wari Pacha Urqu".split(),
        mid=E4 + "ka ri pa wa".split(),
        suf="marka pampa qocha llaqta kancha wasi rumi yoc chaca bamba cota pata llo ta ri sa".split(),
        m="Amaru Huayna Kusi Wayra Antay Illari Yupanqui Mayta Sinchi Rumi Tupaq Kallpa".split(),
        f="Killa Sumaq Tika Chaska Nina Ayra Wayta Qori Yaku Sisa Urpi Asiri".split(),
        casas="Mayta Quispe Mamani Condori Huaman Apaza Yupanqui Choque Ticona Sinchi Kallpa Wari".split(),
    ),
    "cerezo": dict(  # japonesa
        es="de los cerezos", en="Blossom",
        pre="Haru Aki Kaze Tsuki Yama Hana Sora Kiri Mizu Hoshi Kuro Shira Mori Taka Yuki Ishi".split(),
        mid=E4 + "no ga ra ki".split(),
        suf="mura saki gawa shima hara mine zaki kubo da no kawa yama zawa tani hama shiro".split(),
        m="Haruto Kenji Takeshi Ren Daichi Hiro Sora Kaito Isamu Ryo Sho Akio".split(),
        f="Hana Yuki Aiko Sakura Mei Rin Emi Asami Kaede Hikari Natsu Misaki".split(),
        casas="Kurosawa Mizuhara Yamashiro Hoshino Takamori Shiraishi Kazehaya Tsukimura Morikawa Akizuki Kirishima Isayama".split(),
    ),
    "bambu": dict(  # china
        es="del bambú", en="Bamboo",
        pre="Long Jin Shan Hua Lan Yun Qing Bai Mei Tian Zhu Feng Ling Xi An Jiang".split(),
        mid=E4 + E4,
        suf="shan he zhou ling lan nan yang ping cheng quan hai ning feng tai yuan xi".split(),
        m="Wei Jun Hao Ming Lei Bo Zhen Tao Kang Liang Shen Yu".split(),
        f="Mei Lan Xiu Lian Ying Hua Jing Yun Lin Qiao Ning Shu".split(),
        casas="Long Bai Jin Feng Lan Qing Han Zhou Yun Shen Tang Xu".split(),
    ),
    "estepa": dict(  # Asia central / mongola-túrquica
        es="de la estepa", en="Steppe",
        pre="Khar Tem Bat Altan Ulan Bor Ergen Tog Sar Ord Kush Jan Tamir Ak Kar Sel".split(),
        mid=E4 + "a u i e".split(),
        suf="baatar khan tai gol tur dar kent bek sar ul tash yurt ar mur dag gan".split(),
        m="Temur Batu Arslan Bolat Erden Altan Kubat Sanjar Toghrul Daulet Ganbold Oktai".split(),
        f="Saran Aigerim Khulan Ayana Altyn Dilnaz Gerel Saule Tsetseg Aruzhan Nomin Oyuna".split(),
        casas="Kharatai Tamirbek Ulanbor Altankhan Batargal Sarakhan Ordubek Kushtai Togrul Erdenbaatar Akhtash Selenge".split(),
    ),
    "islas": dict(  # Polinesia / Hawái / Oceanía
        es="isleña", en="Islander",
        pre="Ka Lei Moa Hono Mana Wai Ao Kai Nalu Mo Pua Lani Rua Ara Tia Moe".split(),
        mid=E4 + "ka lo ni ma".split(),
        suf="lani lua kai nui hana pule moana roa tahi wai hiku ula motu nalu kohe rangi".split(),
        m="Kai Makoa Nalu Ikaika Koa Kahiau Manaia Tavita Hemi Tamati Keanu Ioane".split(),
        f="Leilani Malia Kalani Moana Aroha Mere Hina Kailani Noelani Ana Mahina Tiare".split(),
        casas="Kealoha Makoa Nohea Kahale Ngata Paewai Faleolo Tupou Mahoe Kanoa Akana Ruatapu".split(),
    ),
}

# Orden de detección: la primera que encaje gana. "llanura" es la de por defecto.
ORDEN = ["islas", "norte", "cerezo", "bambu", "selva", "andina", "sabana", "desierto", "estepa", "costa"]

BIOMAS = {
    "islas": ["minecraft:warm_ocean", "minecraft:lukewarm_ocean", "minecraft:deep_lukewarm_ocean"],
    "norte": ["#minecraft:is_taiga", "minecraft:snowy_plains", "minecraft:ice_spikes", "minecraft:snowy_slopes",
              "minecraft:frozen_peaks", "minecraft:jagged_peaks", "minecraft:grove", "minecraft:frozen_river",
              "minecraft:snowy_beach", "minecraft:frozen_ocean", "minecraft:deep_frozen_ocean",
              {"id": "#c:is_snowy", "required": False}, {"id": "#c:is_icy", "required": False}],
    "cerezo": ["minecraft:cherry_grove", {"id": "#c:is_cherry", "required": False}],
    "bambu": ["minecraft:bamboo_jungle", {"id": "#c:is_bamboo", "required": False}],
    "selva": ["#minecraft:is_jungle", "minecraft:mangrove_swamp", {"id": "#c:is_jungle", "required": False}],
    "andina": ["#minecraft:is_badlands", {"id": "#c:is_badlands", "required": False}],
    "sabana": ["#minecraft:is_savanna", {"id": "#c:is_savanna", "required": False}],
    "desierto": ["minecraft:desert", {"id": "#c:is_desert", "required": False}],
    "estepa": ["minecraft:windswept_hills", "minecraft:windswept_gravelly_hills", "minecraft:windswept_forest",
               "minecraft:windswept_savanna", "minecraft:stony_peaks", "minecraft:meadow",
               {"id": "#c:is_mountain", "required": False}],
    "costa": ["#minecraft:is_beach", "#minecraft:is_ocean", "minecraft:stony_shore", "minecraft:mushroom_fields",
              {"id": "#c:is_beach", "required": False}, {"id": "#c:is_ocean", "required": False}],
}

for k, c in CULTURAS.items():
    for campo, n in (("pre", 16), ("mid", 8), ("suf", 16), ("m", 12), ("f", 12), ("casas", 12)):
        assert len(c[campo]) == n, (k, campo, len(c[campo]))
