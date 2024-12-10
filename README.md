# Repositoryn tarkoitus
Sisältää lähdekoodin palvelulle [rata.digitraffic.fi](https://rata.digitraffic.fi/)

# Arkkitehtuuri

rata.digitraffic.fi koostuu kolmesta komponentista:

* AvoinDataServer
    * Tarjoaa tietoja ulospäin
  * AvoindataUpdater
      * Hakee tietoja lähdejärjestelmistä ja pitää tietokantaa siistinä
      * HTTP-serveri pyörii täällä healthcheckkiä ja manuaalista jobien triggeröintiä varten
      * Alla esimerkkejä
      ```
      curl "http://localhost:18081/reinitialize?date=2024-11-25"  
      curl "http://localhost:18081/reinitialize-compositions?date=2024-11-25"
      curl "http://localhost:18081/extract"
      curl "http://localhost:18081/gtfs"
      curl "http://localhost:18081/gtfs-dev"
      ```
  
* AvoinDataCommon
    * Yhteisiä toimintoja em. mainituille komponenteille. Mm. tietokannasta haku
