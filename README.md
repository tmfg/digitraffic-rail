# Repositoryn tarkoitus
Sisältää lähdekoodin palvelulle [rata.digitraffic.fi](https://rata.digitraffic.fi/)

# Arkkitehtuuri

rata.digitraffic.fi koostuu neljästä komponentista:

* AvoinDataServer
* * Tarjoilee jsonia ulospäin
* AvoindataUpdater
* * Hakee tietoja lähdejärjestelmistä ja pitää tietokantaa siistinä
* LiikeInterface
* * Liike-järjetelmässä sijaitseva rajapinta, jota kautta AvoinDataUpdater hakee tietonsa
* AvoinDataCommon
* * Yhteisiä toimintoja em. mainituille komponenteille. Mm. tietokannasta haku