# Repositoryn tarkoitus
Sisältää lähdekoodin palvelulle [rata.digitraffic.fi](https://rata.digitraffic.fi/)

# Arkkitehtuuri

rata.digitraffic.fi koostuu neljästä komponentista:

* AvoinDataServer
    * Tarjoaa tietoja ulospäin
* AvoindataUpdater
    * Hakee tietoja lähdejärjestelmistä ja pitää tietokantaa siistinä
* AvoinDataCommon
    * Yhteisiä toimintoja em. mainituille komponenteille. Mm. tietokannasta haku
