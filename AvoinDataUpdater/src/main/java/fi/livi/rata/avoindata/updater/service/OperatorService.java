package fi.livi.rata.avoindata.updater.service;

import fi.livi.rata.avoindata.common.dao.metadata.OperatorRepository;
import fi.livi.rata.avoindata.common.dao.metadata.OperatorTrainNumberRepository;
import fi.livi.rata.avoindata.common.domain.metadata.Operator;
import fi.livi.rata.avoindata.common.domain.metadata.OperatorTrainNumber;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class OperatorService {
    @Autowired
    private OperatorRepository operatorRepository;

    @Autowired
    private OperatorTrainNumberRepository operatorTrainNumberRepository;

    @Transactional
    public void update(final Operator[] operators) {
        operatorTrainNumberRepository.deleteAllInBatch();
        operatorRepository.deleteAllInBatch();

        operatorRepository.persist(Arrays.asList(operators));

        List<OperatorTrainNumber> operatorTrainNumbers = new ArrayList<>();
        for (final Operator operator : operators) {
            operatorTrainNumbers.addAll(operator.trainNumbers);
        }

        operatorTrainNumberRepository.persist(operatorTrainNumbers);
    }
}
