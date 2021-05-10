package fi.livi.rata.avoindata.updater.updaters;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import fi.livi.rata.avoindata.common.domain.cause.CategoryCode;
import fi.livi.rata.avoindata.common.domain.cause.DetailedCategoryCode;
import fi.livi.rata.avoindata.common.domain.cause.ThirdCategoryCode;
import fi.livi.rata.avoindata.updater.service.CategoryCodeService;

@Service
public class CategoryCodeUpdater extends AEntityUpdater<CategoryCode[]> {
    @Autowired
    private CategoryCodeService categoryCodeService;

    @Value("${updater.reason.api-key}")
    private String apiKey;

    @Value("${updater.reason.reason-code-path}")
    private String reasonCodePath;

    @Value("${updater.reason.reason-category-path}")
    private String reasonCategoryPath;

    //Every midnight 1:11
    @Override
    @Scheduled(cron = "0 1 11 * * ?")
    protected void update() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
        headers.set("API-KEY", apiKey);

        HttpEntity<String> entity = new HttpEntity<String>("parameters", headers);

        String basePath = "https://laadunvarmistus.rata.liikenteenohjaus.fi/syykoodisto-api";
        String reasonCodePath = "/v1/reason-codes/latest";
        String reasonCategoryPath = "/v1/reason-categories/latest";

        ResponseEntity<JsonNode> reasonCategoryEntity = this.restTemplate.exchange(basePath + reasonCategoryPath, HttpMethod.GET, entity, JsonNode.class);
        ResponseEntity<JsonNode> reasonCodeEntity = this.restTemplate.exchange(basePath + reasonCodePath, HttpMethod.GET, entity, JsonNode.class);

        CategoryCode[] categoryCodes = this.merge(reasonCategoryEntity.getBody(), reasonCodeEntity.getBody());

        this.persist(reasonCategoryPath + reasonCodePath, this.categoryCodeService::update, categoryCodes);
    }

    private CategoryCode[] merge(JsonNode reasonCategoryResult, JsonNode reasonCodeResult) {
        Map<Long, CategoryCode> categoryCodes = new HashMap<>();

        for (JsonNode childElement : reasonCategoryResult) {
            CategoryCode categoryCode = parseCategoryCode(childElement);
            categoryCodes.put(categoryCode.id, categoryCode);
        }

        for (JsonNode childElement : reasonCodeResult) {
            if (childElement.get("visibilityRestricted").asBoolean() == false) {
                DetailedCategoryCode detailedCategoryCode = parseDetailedCategoryCode(childElement);
                CategoryCode categoryCode = categoryCodes.get(oidToid(childElement.get("reasonCategoryOid").asText()));

                detailedCategoryCode.categoryCode = categoryCode;
                categoryCode.detailedCategoryCodes.add(detailedCategoryCode);

                for (JsonNode detailedReasonCodeElement : childElement.get("detailedReasonCodes")) {
                    ThirdCategoryCode thirdCategoryCode = parseThirdCategoryCode(detailedReasonCodeElement);

                    thirdCategoryCode.detailedCategoryCode = detailedCategoryCode;
                    detailedCategoryCode.thirdCategoryCodes.add(thirdCategoryCode);
                }
            }
        }

        return categoryCodes.values().toArray(new CategoryCode[0]);
    }

    private CategoryCode parseCategoryCode(JsonNode categoryCodeElement) {
        CategoryCode categoryCode = new CategoryCode();
        categoryCode.categoryCode = categoryCodeElement.get("code").textValue();
        categoryCode.categoryName = categoryCodeElement.get("name").textValue();
        categoryCode.validFrom = LocalDate.parse(categoryCodeElement.get("validFromDate").textValue());
        categoryCode.validTo = categoryCodeElement.get("validUntilDate").isNull() ? null : LocalDate.parse(categoryCodeElement.get("validUntilDate").textValue());
        categoryCode.id = oidToid(categoryCodeElement.get("oid").asText());
        return categoryCode;
    }

    private Long oidToid(String oid) {
        String[] oidSplit = oid.split("\\.");
        return Long.parseLong(oidSplit[oidSplit.length - 3] + oidSplit[oidSplit.length - 2] + oidSplit[oidSplit.length - 1]);
    }

    private ThirdCategoryCode parseThirdCategoryCode(JsonNode thirdCategoryElement) {
        ThirdCategoryCode thirdCategoryCode = new ThirdCategoryCode();
        thirdCategoryCode.thirdCategoryCode = thirdCategoryElement.get("code").textValue();
        thirdCategoryCode.thirdCategoryName = thirdCategoryElement.get("name").textValue();
        thirdCategoryCode.validFrom = LocalDate.parse(thirdCategoryElement.get("validFromDate").textValue());
        thirdCategoryCode.validTo = thirdCategoryElement.get("validUntilDate").isNull() ? null : LocalDate.parse(thirdCategoryElement.get("validUntilDate").textValue());
        thirdCategoryCode.id = oidToid(thirdCategoryElement.get("oid").asText());
        return thirdCategoryCode;
    }

    private DetailedCategoryCode parseDetailedCategoryCode(JsonNode detailedCategoryElement) {
        DetailedCategoryCode detailedCategoryCode = new DetailedCategoryCode();
        detailedCategoryCode.detailedCategoryCode = detailedCategoryElement.get("code").textValue();
        detailedCategoryCode.detailedCategoryName = detailedCategoryElement.get("name").textValue();
        detailedCategoryCode.validFrom = LocalDate.parse(detailedCategoryElement.get("validFromDate").textValue());
        detailedCategoryCode.validTo = detailedCategoryElement.get("validUntilDate").isNull() ? null : LocalDate.parse(detailedCategoryElement.get("validUntilDate").textValue());
        detailedCategoryCode.id = oidToid(detailedCategoryElement.get("oid").asText());
        return detailedCategoryCode;
    }
}
