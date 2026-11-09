# Confluence-sivut (SIRI)

Tämän kansion tiedostot ovat Confluencen SIRI-sivujen sisältö suomeksi. Ne ovat tiivis versio englanninkielisistä tiedostoista `docs/SIRI*.md`. Totuus on aina `docs/SIRI*.md` ja koodi. Tämän kansion tiedostot päivitetään niiden mukaan.

Sivurakenne Confluencessa:

- `SIRI.md` (juurisivu)
    - `SIRI-ET.md`
    - `SIRI-SX.md`
    - `SIRI-VM.md`

## Julkinen repo

Repo on julkinen. Älä kirjoita tänne sisäisiä tai salaisia tietoja: sisäisiä osoitteita, tunnuksia, tuotannon JVM-asetuksia tai tietokantalukemia. Kirjoita vain se, mikä on jo julkisissa `docs/SIRI*.md`-tiedostoissa.

## Päivitys

1. Kun `docs/SIRI*.md` tai SIRI-koodi muuttuu, päivitä vastaava tiedosto tässä kansiossa samassa PR:ssä.
2. Tarkista väitteet koodista, älä pelkästään englanninkielisestä dokumentista.
3. Kopioi tiedoston sisältö käsin Confluence-sivulle. Confluence Cloud tunnistaa markdownin liitettäessä. Vaihtoehto: avaa tiedosto VS Coden esikatselussa, kopioi esikatselusta ja liitä.
4. Lisää Confluencessa käsin ne osat, joita markdown ei tue: sisällysluettelo (`/toc`) ja mahdolliset info-paneelit (`/info`).

## Mitä tänne kirjoitetaan ja mitä ei

Mukaan:

- mitä rajapinta antaa, miten dokumentti muodostetaan, säännöt, kenttätaulukot, virhetilanteet, asetukset, rajoitukset ja seuranta.

Pois:

- testit ja testiluokkien nimet
- luokkien nimet (lokien ja asetusten nimet saa mainita)
- suunnittelupohdinnat ja hylätyt vaihtoehdot (esim. `visitIndex`, rivijärjestys)
- tuotannon mittaluvut ja sisäiset arvot

## Kirjoitustyyli

- Kieli on suomi, lyhyesti ja ilman turhaa jargonia.
- Sano "dokumentti" tuotetusta XML-tiedostosta (ei "syöte" eikä "feed") ja "rajapinta" palvelusta.
- NeTEx-tiedostosta sanotaan "NeTEx package".
- Jokaisen sivun alussa on rivi, joka kertoo AI:n käytöstä, ja rivi `Lähde: [docs/SIRI-xx.md](GitHub-linkki)`.
- Avoimia asioita ei esitetä tosiasioina. Merkitse ne selvästi avoimiksi.
