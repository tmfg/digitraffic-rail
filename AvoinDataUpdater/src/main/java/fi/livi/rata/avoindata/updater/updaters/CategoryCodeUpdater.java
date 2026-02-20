package fi.livi.rata.avoindata.updater.updaters;

import static fi.livi.rata.avoindata.updater.config.WebClientConfiguration.BLOCK_DURATION;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Strings;

import fi.livi.rata.avoindata.common.domain.cause.CategoryCode;
import fi.livi.rata.avoindata.common.domain.cause.DetailedCategoryCode;
import fi.livi.rata.avoindata.common.domain.cause.ThirdCategoryCode;
import fi.livi.rata.avoindata.updater.service.CategoryCodeService;

@Service
public class CategoryCodeUpdater extends AEntityUpdater<CategoryCode[]> {
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private CategoryCodeService categoryCodeService;

    private final String syykoodiApiPath;

    private final WebClient webClient;

    public CategoryCodeUpdater(final WebClient ripaWebClient,
                               final @Value("${updater.reason.syykoodisto-api-path}") String syykoodiApiPath) {
        this.syykoodiApiPath = syykoodiApiPath;
        this.webClient = ripaWebClient.mutate().baseUrl(syykoodiApiPath).build();
    }

    //Every day 11:01
    @Override
    @Scheduled(cron = "0 1 11 * * ?")
    protected void update() {
        log.info("Updating CategoryCodes from " + syykoodiApiPath);

        if (Strings.isNullOrEmpty(syykoodiApiPath)) {
            return;
        }

        final String reasonCodePath = "/v1/reason-codes/latest";
        final String reasonCategoryPath = "/v1/reason-categories/latest";

        final JsonNode reasonCategoryEntity = webClient.get().uri(reasonCategoryPath).retrieve().bodyToMono(JsonNode.class).block(BLOCK_DURATION);
        final JsonNode reasonCodeEntity = webClient.get().uri(reasonCodePath).retrieve().bodyToMono(JsonNode.class).block(BLOCK_DURATION);

        final CategoryCode[] categoryCodes = this.merge(reasonCategoryEntity, reasonCodeEntity);

        log.info("Found {} categoryCodes", categoryCodes.length);

        this.persist("categorycodes", this.categoryCodeService::update, categoryCodes);
    }

    private CategoryCode[] merge(final JsonNode reasonCategoryResult, final JsonNode reasonCodeResult) {
        final Map<String, CategoryCode> categoryCodes = new HashMap<>();

        for (final JsonNode childElement : reasonCategoryResult) {
            final CategoryCode categoryCode = parseCategoryCode(childElement);
            categoryCodes.put(categoryCode.oid, categoryCode);
        }

        for (final JsonNode childElement : reasonCodeResult) {
            if (!childElement.get("visibilityRestricted").asBoolean()) {
                final DetailedCategoryCode detailedCategoryCode = parseDetailedCategoryCode(childElement);
                final CategoryCode categoryCode = categoryCodes.get(childElement.get("reasonCategoryOid").asText());

                detailedCategoryCode.categoryCode = categoryCode;
                categoryCode.detailedCategoryCodes.add(detailedCategoryCode);

                for (final JsonNode detailedReasonCodeElement : childElement.get("detailedReasonCodes")) {
                    if (!detailedReasonCodeElement.get("visibilityRestricted").asBoolean()) {
                        final ThirdCategoryCode thirdCategoryCode = parseThirdCategoryCode(detailedReasonCodeElement);

                        thirdCategoryCode.detailedCategoryCode = detailedCategoryCode;
                        detailedCategoryCode.thirdCategoryCodes.add(thirdCategoryCode);
                    }
                }
            }
        }

        return categoryCodes.values().toArray(new CategoryCode[0]);
    }

    private CategoryCode parseCategoryCode(final JsonNode categoryCodeElement) {
        final CategoryCode categoryCode = new CategoryCode();
        categoryCode.categoryCode = categoryCodeElement.get("code").textValue();
        categoryCode.categoryName = categoryCodeElement.get("name").textValue();
        categoryCode.validFrom = LocalDate.parse(categoryCodeElement.get("validFromDate").textValue());
        categoryCode.validTo = categoryCodeElement.get("validUntilDate").isNull() ? null : LocalDate.parse(categoryCodeElement.get("validUntilDate").textValue());
        categoryCode.oid = categoryCodeElement.get("oid").asText();
        return categoryCode;
    }

    private ThirdCategoryCode parseThirdCategoryCode(final JsonNode thirdCategoryElement) {
        final ThirdCategoryCode thirdCategoryCode = new ThirdCategoryCode();
        thirdCategoryCode.thirdCategoryCode = thirdCategoryElement.get("code").textValue();
        thirdCategoryCode.thirdCategoryName = thirdCategoryElement.get("name").textValue();
        thirdCategoryCode.validFrom = LocalDate.parse(thirdCategoryElement.get("validFromDate").textValue());
        thirdCategoryCode.validTo = thirdCategoryElement.get("validUntilDate").isNull() ? null : LocalDate.parse(thirdCategoryElement.get("validUntilDate").textValue());
        thirdCategoryCode.oid = thirdCategoryElement.get("oid").asText();
        return thirdCategoryCode;
    }

    private DetailedCategoryCode parseDetailedCategoryCode(final JsonNode detailedCategoryElement) {
        final DetailedCategoryCode detailedCategoryCode = new DetailedCategoryCode();
        detailedCategoryCode.detailedCategoryCode = detailedCategoryElement.get("code").textValue();
        detailedCategoryCode.detailedCategoryName = detailedCategoryElement.get("name").textValue();
        detailedCategoryCode.validFrom = LocalDate.parse(detailedCategoryElement.get("validFromDate").textValue());
        detailedCategoryCode.validTo = detailedCategoryElement.get("validUntilDate").isNull() ? null : LocalDate.parse(detailedCategoryElement.get("validUntilDate").textValue());
        detailedCategoryCode.oid = detailedCategoryElement.get("oid").asText();
        return detailedCategoryCode;
    }
}
