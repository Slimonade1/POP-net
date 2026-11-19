package dk.pop.status.temp.utils;

import dk.pop.status.temp.domain.WebhookDestination;
import dk.pop.status.temp.domain.WebhookDestination.Provider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class WebhookDestinationUtilsTest {
    @Test
    void discordRequestsConfirmationAndPreservesThreadId() {
        var uri = WebhookDestinationUtils.deliveryUri(Provider.DISCORD,
                "https://discord.com/api/v10/webhooks/123/token?thread_id=456&wait=false");
        assertThat(uri.getQuery()).isEqualTo("thread_id=456&wait=true");
    }

    @Test
    void acceptsSlackAndGovSlackUrls() {
        for (String host : new String[]{"hooks.slack.com", "hooks.slack-gov.com"}) {
            String url = "https://" + host + "/services/T1/B1/token";
            assertThat(WebhookDestinationUtils.deliveryUri(Provider.SLACK, " " + url + " ").toString())
                    .isEqualTo(url);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://discord.com/api/webhooks/123/token",
            "https://discord.com.evil.test/api/webhooks/123/token",
            "https://discord.com@evil.test/api/webhooks/123/token",
            "https://user:password@discord.com/api/webhooks/123/token",
            "https://discord.com:8080/api/webhooks/123/token",
            "https://discord.com/api/webhooks/123/token#fragment",
            "https://discord.com/unrelated/path",
            "https://hooks.slack.com/services/T1/B1/token",
            "not a URL", ""
    })
    void rejectsInvalidOrMismatchedDiscordUrls(String url) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> WebhookDestinationUtils.deliveryUri(Provider.DISCORD, url));
    }

    @Test
    void rejectsMissingProviderAndUrlWithoutExposingTheSecret() {
        assertThatIllegalArgumentException().isThrownBy(() -> WebhookDestinationUtils.deliveryUri(null, "secret"))
                .withMessageNotContaining("secret");
        assertThatIllegalArgumentException().isThrownBy(() -> WebhookDestinationUtils.deliveryUri(Provider.SLACK, null));
        assertThatIllegalArgumentException().isThrownBy(() -> WebhookDestinationUtils.deliveryUri(Provider.SLACK, "bad secret URL"))
                .withMessageNotContaining("bad secret URL");
    }

    @Test
    void destinationToStringDoesNotExposeTheUrl() {
        var destination = new WebhookDestination(1L, "Team", Provider.SLACK,
                "https://hooks.slack.com/services/T1/B1/secret-token", true);
        assertThat(destination.toString()).contains("url=[hidden]").doesNotContain("secret-token", "https://");
    }
}
