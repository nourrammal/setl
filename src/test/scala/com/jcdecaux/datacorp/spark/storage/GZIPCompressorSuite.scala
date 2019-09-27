package com.jcdecaux.datacorp.spark.storage

import org.scalatest.FunSuite

class GZIPCompressorSuite extends FunSuite {

  val compressor = new GZIPCompressor
  val str = "Q: I am working on a highly secure application and need to compress data such as string and byte arrays. I am using the java.util.zip.* classes, but I am having some problems.\n\nFirst, when using the Deflator and Inflator classes, I get DataFormatExceptions when the string is less than 30 characters.\n\nSecond, I have a question about the compression itself. I am using ByteArrayOutputStream and DeflaterOutputStream . I noticed that the compressdata.length() > OriginalData.length() where OriginalData is the uncompressed data. It doesn't seem to make sense that the compressed length is longer than the uncompressed length. Can this be right?\n\nA: In order to answer the first part of your question, I tested a string less than 30 characters and one greater than 30 characters. The only time that I could get a DataFormatException was when Inflater and Deflater were constructed with different nowrap values. Be sure that the Inflater and Deflater specify nowrap the same way. If the Deflater sets nowrap to false, the Inflater must do the same. Likewise, if the Deflater sets it to true, the Inflater must set it to true.\n\nWhether or not to set nowrap to true or false depends on your needs. A true nowrap omits the ZLIB header and checksum data from the compressed data. A false no wrap leaves it. However, the Inflater's nowrap must be set to match the compressed input. Otherwise, as we have seen, you will get a DataFormatException.\n\nYour second question raises an important fact about data compression. As strange as it may seem, the compressed data size can be larger than the uncomp ressed size. Depending on your Deflater settings, the Deflater may append a header to the compressed data. This header is used to decode the information and check it for errors. If you deal with very small strings, it is likely that not much real compression has gone on. Cutting a string of 30 characters to 15, while a 50 percent reduction, is only a reduction of 15 characters. As a result, the added size of the header makes the compressed string longer than the original. You will not see the benefits of compression until your data reaches a certain larger, precompression size. It's hard to say what this size is, but generically it is where: (compressed size + header size) < uncompressed size. If your data is not large enough, you're wasting time using compression.\n\nYou may also want to consider some of the other compression settings. Some compression algorithms are optimized for time, while others achieve a better compression but take longer to decompress. So the algorithm that you choose goes a long way in determining the final size of your compressed data."
  val str2 = "同一年，日耳曼民族的法兰克人首领克洛维打败罗马人，建立墨洛温家族的法兰克王国。其后法兰克王国不断发展壮大，在加洛林家族的查理大帝的统治之下王国达到鼎盛，征服国土范围到今法国、德国、荷兰、瑞士、北意大利、波希米亚、奥地利西部、伊比利亚半岛东北角的领土。800年的圣诞节，查理大帝在罗马礼拜时被教宗利奥三世加冕为“罗马人的皇帝”[8][9]，整个法兰克王国也被称为“加洛林帝国”，西罗马帝国就此以帝位转移至法兰克国王的形式复辟。加洛林帝国奠定了后世的神圣罗马帝国的基石，直到1806年神圣罗马帝国被取消为止。\n\n\n843年，凡尔登条约将查理曼帝国一分为三。\n840年，查理大帝之子路易一世去世，他的帝国也随之分崩离析。843年，路易一世的三个儿子订立凡尔登条约，全国分为三部分。其中查理大帝的长孙洛塔尔承袭皇帝称号，并领有自莱茵河下游以南、经罗纳河流域，至今意大利中部地区的疆域，为中法兰克王国。而他的弟弟日耳曼人路易，分得莱茵河以东地区，为东法兰克王国。另一个弟弟秃头查理则领有除此之外的西部地区，为西法兰克王国。\n\n查理曼死后，841年胖子查理(东法兰克)和秃头查理(西法兰克)联合起来打败了长兄，“罗马皇帝”这个头衔，始终由东法兰克王国和西法兰克王国的加洛林君主轮流拥有。帝国皇冠最初在西法兰克和东法兰克之间争夺不休，先是作为战利品先后落到西边秃头查理和东边胖子查理。870年，胖子查理和秃头查理签订墨尔森条约，瓜分了长兄的中法兰克王国。\n\n胖子查理于887年被废黜与888年死后，加洛林帝国自此分裂瓦解，此后未恢复，并再也没有统一。自胖子查理之后，罗马帝国皇帝的头衔拥有者大多是由教宗加冕的意大利国王，意大利国王的实际统治范围极其有限，仅限于意大利东北部，而那些国王几乎是意大利本土贵族，最后一位这样的皇帝是死于924年的意大利人贝伦加尔一世。根据Regino of Prüm的说法，帝国的每一部分从自己内部选出了一位“小王”（kinglet）。"
  val str3 = "Almace est l'épée légendaire de Turpin, archevêque de Reims, chevalier et un des douze pairs de Charlemagne dans plusieurs chansons de geste du cycle carolingien.\n\nCes œuvres très populaires en Europe entre le xie siècle et le xive siècle participent à la construction du mythe de personnages tels que Charlemagne, Roland ou Turpin. Elles racontent leurs exploits militaires, essentiellement contre les Sarrasins, et sont donc amenées à présenter leurs armes.\n\nLe fait qu'il soit donné un nom propre à Almace, comme à Durandal et Joyeuse, les épées de Roland et de Charlemagne, indique l'importance du personnage de Turpin dans ce corpus littéraire. Mais les chansons de geste médiévales n'évoquent que rarement cette épée et n'en proposent aucune description détaillée. La mention principale réside dans la Chanson de Roland où Turpin l'utilise à la fin de la bataille de Roncevaux alors qu'il est devenu clair que celle-ci est perdue et que l'archevêque va succomber à son tour sous les assauts des Sarrasins.\n\nLa Chanson de Gaufrey donne à l'épée une origine sarrasine. Des analyses onomastiques proposent d'ailleurs que le nom « Almace » soit issu de l'arabe. Ces études ne font cependant pas l'objet de consensus, certains romanistes préférant des sources plus conformes à l'idée d'une épée « bonne pour abattre les païens », selon l'expression prêtée à Charlemagne par la Karlamagnús saga après qu'il a éprouvé l'arme.\n\nLes chansons de geste du cycle carolingien font de l'archevêque Turpin un héros de la foi chrétienne qui décime les païens, bénit les soldats sur le champ de bataille, baptise les infidèles convertis, et parfois même marie les chevaliers. Il n'est donc pas surprenant qu'une épée portant le nom de l'arme mythique se retrouve parmi d'autres reliques du Moyen Âge dans le trésor de l'abbaye de Saint-Denis, dont les différents inventaires réalisés entre le début du xvie siècle et la fin du xviiie siècle fournissent une description succincte. L'arme finit par disparaître du trésor, probablement à l'époque de la Révolution française.\n\nDe nos jours, on retrouve Almace dans plusieurs jeux vidéo de rôle qui peuvent lui attribuer des pouvoirs magiques comme celui de geler les ennemis de son détenteur."
  val str4 = "A wiki enables communities of editors and contributors to write documents collaboratively. All that people require to contribute is a computer, Internet access, a web browser, and a basic understanding of a simple markup language (e.g., HTML). A single page in a wiki website is referred to as a \"wiki page\", while the entire collection of pages, which are usually well-interconnected by hyperlinks, is \"the wiki\". A wiki is essentially a database for creating, browsing, and searching through information. A wiki allows non-linear, evolving, complex, and networked text, while also allowing for editor argument, debate, and interaction regarding the content and formatting.[9] A defining characteristic of wiki technology is the ease with which pages can be created and updated. Generally, there is no review by a moderator or gatekeeper before modifications are accepted and thus lead to changes on the website. Many wikis are open to alteration by the general public without requiring registration of user accounts. Many edits can be made in real-time and appear almost instantly online, but this feature facilitates abuse of the system. Private wiki servers require user authentication to edit pages, and sometimes even to read them. Maged N. Kamel Boulos, Cito Maramba, and Steve Wheeler write that the open wikis produce a process of Social Darwinism. \"'Unfit' sentences and sections are ruthlessly culled, edited, and replaced if they are not considered 'fit', which hopefully results in the evolution of a higher quality and more relevant page. While such openness may invite 'vandalism' and the posting of untrue information, this same openness also makes it possible to rapidly correct or restore a 'quality' wiki page."

  test("GZIPCompressor should be able to compress a string to a Byte[]") {
    println(s"String1: ${str.getBytes().length} -> ${compressor.compress(str).length}")
    println(s"String2: ${str2.getBytes().length} -> ${compressor.compress(str2).length}")
    println(s"String3: ${str3.getBytes().length} -> ${compressor.compress(str3).length}")
    println(s"String4: ${str4.getBytes().length} -> ${compressor.compress(str4).length}")
    assert(str.getBytes().length >= compressor.compress(str).length)
    assert(str2.getBytes().length >= compressor.compress(str2).length)
    assert(str3.getBytes().length >= compressor.compress(str3).length)
    assert(str4.getBytes().length >= compressor.compress(str4).length)

  }

  test("GZIPCompressor should be able to decompress a Byte array to string") {
    assert(compressor.decompress(compressor.compress(str)) === str)
    assert(compressor.decompress(compressor.compress(str2)) === str2)
    assert(compressor.decompress(compressor.compress(str3)) === str3)
    assert(compressor.decompress(compressor.compress(str4)) === str4)
    assert(compressor.decompress("testtesttest".getBytes()) === "testtesttest")

  }

}
