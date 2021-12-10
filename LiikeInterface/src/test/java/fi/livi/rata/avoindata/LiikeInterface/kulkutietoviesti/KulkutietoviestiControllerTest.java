package fi.livi.rata.avoindata.LiikeInterface.kulkutietoviesti;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

import java.time.LocalDate;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import fi.livi.rata.avoindata.LiikeInterface.BaseTest;
import fi.livi.rata.avoindata.LiikeInterface.domain.JunapaivaPrimaryKey;
import fi.livi.rata.avoindata.LiikeInterface.domain.entities.kulkutietoviesti.Kulkutietoviesti;
import fi.livi.rata.avoindata.LiikeInterface.jupatapahtuma.repository.JunapaivaRepository;
import fi.livi.rata.avoindata.LiikeInterface.kulkutietoviesti.repository.KulkutietoviestiRepository;

@Ignore
public class KulkutietoviestiControllerTest extends BaseTest {
    @Autowired
    private KulkutietoviestiController kulkutietoviestiController;

    @MockBean
    private KulkutietoviestiRepository kulkutietoviestiRepository;

    @MockBean
    private JunapaivaRepository junapaivaRepository;

    @Test
    public void kulkutietoviestiShouldNotBeFilteredIfNotClassified() {
        Kulkutietoviesti kulkutietoviesti = new Kulkutietoviesti();
        kulkutietoviesti.junanumero = "1";
        kulkutietoviesti.lahtopvm = LocalDate.of(2018, 1, 1);
        kulkutietoviesti.tapahtumaV = LocalDate.of(2018, 1, 1);
        when(kulkutietoviestiRepository.findByVersioGreaterThan(eq(1L), any())).thenReturn(Lists.newArrayList(kulkutietoviesti));
        when(junapaivaRepository.findClassifiedTrains(any(Iterable.class))).thenReturn(Sets.newHashSet(new JunapaivaPrimaryKey("1", LocalDate.of(2018, 1, 2))));

        Assert.assertEquals(1, kulkutietoviestiController.getTrainRunningMessages(1L).size());
    }

    @Test
    public void classifiedKulkutietoViestiShouldBeFilteredOkay() {
        Kulkutietoviesti kulkutietoviesti = new Kulkutietoviesti();
        kulkutietoviesti.junanumero = "1";
        kulkutietoviesti.lahtopvm = LocalDate.of(2018, 1, 1);
        kulkutietoviesti.tapahtumaV = LocalDate.of(2018, 1, 1);
        when(kulkutietoviestiRepository.findByVersioGreaterThan(eq(1L), any())).thenReturn(Lists.newArrayList(kulkutietoviesti));
        when(junapaivaRepository.findClassifiedTrains(any(Iterable.class))).thenReturn(Sets.newHashSet(new JunapaivaPrimaryKey("1", LocalDate.of(2018, 1, 1))));

        Assert.assertEquals(0, kulkutietoviestiController.getTrainRunningMessages(1L).size());
    }

    @Test
    public void kulkutietoViestisWithNullShouldBeOkay() {
        Kulkutietoviesti kulkutietoviesti = new Kulkutietoviesti();
        kulkutietoviesti.junanumero = "1";
        kulkutietoviesti.lahtopvm = null;
        kulkutietoviesti.tapahtumaV = LocalDate.of(2018, 1, 1);
        when(kulkutietoviestiRepository.findByVersioGreaterThan(eq(1L), any())).thenReturn(Lists.newArrayList(kulkutietoviesti));
        when(junapaivaRepository.findClassifiedTrains(any(Iterable.class))).thenReturn(Sets.newHashSet(new JunapaivaPrimaryKey("1", LocalDate.of(2018, 1, 1))));

        Assert.assertEquals(0, kulkutietoviestiController.getTrainRunningMessages(1L).size());
    }

}
