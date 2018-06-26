package fi.livi.rata.avoindata.LiikeInterface.services;


import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import fi.livi.rata.avoindata.LiikeInterface.BaseTest;
import fi.livi.rata.avoindata.LiikeInterface.domain.JunapaivaPrimaryKey;
import fi.livi.rata.avoindata.LiikeInterface.jupatapahtuma.repository.JunapaivaRepository;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Matchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;

import static org.mockito.Mockito.when;

public class ClassifiedTrainFilterTest extends BaseTest {
    @Autowired
    private ClassifiedTrainFilter classifiedTrainFilter;

    @MockBean
    private JunapaivaRepository junapaivaRepository;

    @Before
    public void setup() {
        when(junapaivaRepository.findClassifiedTrains(Matchers.any(Iterable.class))).thenReturn(
                Sets.newHashSet(new JunapaivaPrimaryKey("1", LocalDate.of(2017, 1, 1))));
    }

    @Test
    public void classifiedTrainShouldNotBeReturned() {
        JunapaivaPrimaryKey key1 = new JunapaivaPrimaryKey("1", LocalDate.of(2017, 1, 1));
        JunapaivaPrimaryKey key2 = new JunapaivaPrimaryKey("2", LocalDate.of(2017, 1, 1));
        final HashSet<JunapaivaPrimaryKey> keys = Sets.newHashSet(key1, key2);
        final List<JunapaivaPrimaryKey> filteredKeys = Lists.newArrayList(classifiedTrainFilter.filterClassifiedTrains(keys, k -> k));

        Assert.assertEquals(filteredKeys.size(), 1);
        Assert.assertEquals(filteredKeys.get(0), key2);
    }

}