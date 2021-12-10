package fi.livi.rata.avoindata.LiikeInterface.metadata.service;

import fi.livi.rata.avoindata.LiikeInterface.domain.entities.localization.Localizations;
import fi.livi.rata.avoindata.LiikeInterface.metadata.repository.JunalajiRepository;
import fi.livi.rata.avoindata.LiikeInterface.metadata.repository.JunatyyppiRepository;
import fi.livi.rata.avoindata.LiikeInterface.metadata.repository.VetovoimalajiRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class LocalizationService {

    @Autowired
    private JunalajiRepository junalajiRepository;

    @Autowired
    private JunatyyppiRepository junatyyppiRepository;

    @Autowired
    private VetovoimalajiRepository vetovoimalajiRepository;

    public Localizations getLocalizations() {
        return new Localizations(junalajiRepository.findAll(), junatyyppiRepository.findAll(), vetovoimalajiRepository.findAll());
    }
}
