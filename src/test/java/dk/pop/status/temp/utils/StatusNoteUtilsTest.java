package dk.pop.status.temp.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class StatusNoteUtilsTest {
    @Test
    void trimsSurroundingWhitespaceButPreservesParagraphs() {
        assertThat(StatusNoteUtils.normalize("  Printing is unavailable.\nWe are replacing the server.  "))
                .isEqualTo("Printing is unavailable.\nWe are replacing the server.");
    }

    @Test
    void rejectsMissingAndBlankNotes() {
        assertThatIllegalArgumentException().isThrownBy(() -> StatusNoteUtils.normalize(null));
        assertThatIllegalArgumentException().isThrownBy(() -> StatusNoteUtils.normalize(" \n\t "));
    }

    @Test
    void acceptsTheMaximumLengthAndRejectsLongerNotes() {
        assertThat(StatusNoteUtils.normalize("x".repeat(500))).hasSize(500);
        assertThatIllegalArgumentException().isThrownBy(() -> StatusNoteUtils.normalize("x".repeat(501)));
    }
}
