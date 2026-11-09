# SIRI

*Tämän dokumentin kirjoittamisessa on käytetty AI:ta*

*Lähde: [docs/SIRI.md](https://github.com/tmfg/digitraffic-rail-private/blob/master/docs/SIRI.md) (GitHub). Päivitä tämä sivu, kun lähde muuttuu.*

## Mitä SIRI ja Nordic SIRI -profiili ovat

**SIRI** — *Service Interface for Real Time Information* — on eurooppalainen standardi (CEN/TS 15531) reaaliaikaisen joukkoliikennetiedon välittämiseen: esimerkiksi ennustetut saapumis- ja lähtöajat, peruutukset, lisävuorot, laiturimuutokset, ajoneuvojen sijainnit, häiriötiedotteet ja muut. Se on reaaliaikainen vastinpari **NeTEx**-standardille, jolla julkaisemme suunnitellun raideliikenteen aikataulun. SIRI kuvaa lyhyen aikavälin muutoksia suunnitelmaan kuluvan liikennöintipäivän aikana. NeTEx kuvaa itse suunnitelman.

SIRI on laaja standardi, jossa on kymmenen toiminnallista palvelua. **Nordic SIRI -profiili** (Enturin julkaisema) rajaa SIRIn kolmeen palveluun:

- **SIRI-ET — Estimated Timetable:** jatkuvat päivitykset suunniteltuihin vuoroihin.
- **SIRI-VM — Vehicle Monitoring:** kunkin ajoneuvon reaaliaikainen GPS-sijainti.
- **SIRI-SX — Situation Exchange:** tekstimuotoiset häiriö- ja matkustajatiedotteet.

## Palvelut ja rajapinnat

Kaikki kolme palvelua ovat tuotannossa. Jokainen palvelu julkaisee yhden XML-dokumentin, joka generoidaan uudelleen 60 sekunnin välein.

| Palvelu | Rajapinta | Sivu |
| --- | --- | --- |
| SIRI-ET | [rata.digitraffic.fi/api/v1/siri/et](https://rata.digitraffic.fi/api/v1/siri/et) | SIRI-ET |
| SIRI-VM | [rata.digitraffic.fi/api/v1/siri/vm](https://rata.digitraffic.fi/api/v1/siri/vm) | SIRI-VM |
| SIRI-SX | [rata.digitraffic.fi/api/v1/siri/sx](https://rata.digitraffic.fi/api/v1/siri/sx) | SIRI-SX |

- Beta-ympäristö: [rata-beta.digitraffic.fi](https://rata-beta.digitraffic.fi/). OpenAPI: [openapi.json](https://rata.digitraffic.fi/swagger/openapi.json).
- Vastaus on `application/xml`. Headerit `x-is-fresh` (alle 5 minuuttia vanha) ja `x-timestamp`. ET:llä ja VM:llä `x-timestamp` on dokumentin generointiaika. SX:llä se on viimeisen onnistuneen tarkistuksen aika: muuttumaton dokumentti tallennetaan vain kerran, joten `x-is-fresh` kertoo tarkistuksen tuoreuden eikä XML-sisällön ikää.
- Jokainen palvelu voidaan kytkeä päälle tai pois omilla asetuksillaan (`updater.siri.<et|vm|sx>.enabled`, `avoindataserver.siri.<et|vm|sx>.enabled`).

## Yleisiä sääntöjä

Nordic-profiili määrittää myös joukon läpileikkaavia sääntöjä, jotka ohjaavat kaikkia palveluita:

- **Yksi tiedosto per toimitus.** Täydellinen tietojoukko toimitetaan yhtenä XML-dokumenttina.
- **Paikallinen aika.** Kaikki päivämäärät ja kellonajat ovat paikallista (Helsingin) aikaa vähintään sekunnin tarkkuudella, esimerkiksi `2026-07-10T21:22:23`. Julkaisemme aina täydet sekunnit.
- **Kansalliset pysäkkitunnisteet.** Jokaisen pysäkkiviittauksen on oltava virallinen tunniste kansallisesta pysäkkirekisteristä. Raideliikenteessä se tarkoittaa **PETI**-tunnisteita: laiturit ovat `FSR:Quay` (ET ja VM) ja SX:n asemaviesteissä asema on PETI-pysäkki (`StopPlaceRef`). SX:n junaviesteissä on vain matkaviite eikä pysäkkejä. Samat tunnisteet, joita NeTEx-aikataulumme käyttää.
- **Sidottu suunnitelmaan.** Junaan liittyvä reaaliaikainen tieto (ET, VM ja SX:n junaviestit) viittaa NeTEx:issä julkaistuihin tunnisteisiin. Tieto, jota ei voi sitoa julkaistuun vuoroon, pudotetaan. Keksittyä viittausta ei julkaista. SX:n asemaviestit eivät liity vuoroon, joten ne eivät viittaa NeTEx-matkoihin.
- **Tuottajan codespace.** Sekä dokumentin tuottajaviittaus (`ProducerRef`) että datalähdeviittaus (`DataSource`) käyttävät Fintraffic Railin rekisteröityä codespacea, `FTR`.
- **Tuoreus ja täydellisyys.** Dokumentissa ei ole testidataa, ja jokainen dokumentti on itsenäinen (ei riipu muista SIRI-dokumenteista).

## Yhteinen toteutus

- Kaikki palvelut lukevat matkaviitteet (vuoron tunniste, linja, liikennöitsijä) NeTEx-julkaisun yhteydessä tallennetuista tiedoista ja pysäkit PETI-rekisteristä. ET ja VM lukevat lisäksi suunnitellut laiturit. Siksi ET, VM ja SX antavat samalle junalle samat tunnisteet.
- Jos julkaistuja NeTEx-matkoja tai PETI-tietoja ei ole saatavilla, ET ja VM epäonnistuvat eivätkä tallenna mitään. Viimeisin toimiva dokumentti pysyy tarjolla. SX toimii eri tavalla: jos vain NeTEx-matkat puuttuvat, SX julkaisee asemaviestit ja pudottaa junaviestit. Jos PETI-tietoja ei ole, SX epäonnistuu kuten ET ja VM, eikä tuoreita asemaviestejä synny.
- Matkaviitteet tallennetaan päiville tänään−2 … tänään+14 (`updater.netex.persist-journeys.lookback-days=2`, `lookahead-days=14`). NeTEx-paketin XML kattaa noin 423 päivää, eikä se muutu tästä.
- Generoitu XML validoidaan SIRI 2.0 -skeemaa vasten, eikä virheellistä dokumenttia tallenneta.

## Yhteys GTFS-dataan

Digitraffic julkaisee jo osan tästä reaaliaikaisesta datasta **GTFS-Realtime** -muodossa. GTFS-Realtimessa on useampi viestityyppi (`FeedEntity`), ja SIRI-palveluiden vastineet niille ovat:

- **SIRI-ET** vastaa GTFS-Realtimen `TripUpdate`
- **SIRI-VM** vastaa GTFS-Realtimen `VehiclePosition`
- **SIRI-SX** vastaa GTFS-Realtimen `Alert`

## Viitteet

Standardi ja sen profiili:

- [Nordic SIRI -profiili — General information SIRI](https://entur.atlassian.net/wiki/spaces/PUBLIC/pages/637370373/General+information+SIRI)
- [Enturin SIRI-esimerkit](https://github.com/entur/profile-norway-examples/tree/master/siri)
- [GTFS-Realtime](https://gtfs.org/documentation/realtime/reference/)
