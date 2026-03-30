package com.logilink.emailanalyzer.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.logilink.emailanalyzer.domain.Breakdown;
import com.logilink.emailanalyzer.util.EmailDateParser;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@ToString
public class EmailAnalysisResult {

    @JsonProperty("email_id")
    @JsonAlias("emailId")
    private String emailId;

    /**
     * Raw value from model JSON. Kept as string so Spring AI's BeanOutputConverter does not require a
     * JSR-310-enabled ObjectMapper and arbitrary LLM date text still deserializes.
     */
    @JsonProperty("email_date")
    @JsonAlias("emailDate")
    private String emailDateRaw;

    @JsonIgnore
    private LocalDateTime emailDate;

    /**
     * Sets {@link #emailDate} from parsed {@link #emailDateRaw} when possible, otherwise from the
     * message.
     */
    public void resolveEmailDate(LocalDateTime fallbackFromMessage) {
        LocalDateTime parsed = EmailDateParser.parseLlmEmailDate(this.emailDateRaw);
        this.emailDate = parsed != null ? parsed : fallbackFromMessage;
    }

    @JsonProperty("subject")
    private String subject;

    @JsonProperty("sender")
    private String sender;

    @JsonProperty("criticality_score")
    @JsonAlias("criticalityScore")
    private Integer criticalityScore;

    @JsonProperty("criticality_level")
    @JsonAlias("criticalityLevel")
    private String criticalityLevel;

    @JsonProperty("breakdown")
    private Breakdown breakdown;

    @JsonProperty("summary")
    private String summary;

    @JsonProperty("key_risks")
    @JsonAlias("keyRisks")
    private List<String> keyRisks;

    @JsonProperty("affected_stakeholders")
    @JsonAlias("affectedStakeholders")
    private List<String> affectedStakeholders;

    @JsonProperty("action_needed")
    @JsonAlias("actionNeeded")
    private Boolean actionNeeded;

    @JsonProperty("recommended_action")
    @JsonAlias("recommendedAction")
    private String recommendedAction;

    @JsonProperty("estimated_response_time")
    @JsonAlias("estimatedResponseTime")
    private String estimatedResponseTime;

    @JsonProperty("confidence")
    private Integer confidence;

    public boolean isNotProcessedByLLM() {
        return StringUtils.isEmpty(criticalityLevel) || criticalityScore == null || criticalityScore < 0;
    }
}
