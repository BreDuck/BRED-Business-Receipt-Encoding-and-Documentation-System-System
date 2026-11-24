package com.bred.rms.ui.components;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserAvatarTest {
    @Test
    void matchesTheDuckProfileByNormalizedEmail() {
        assertTrue(UserAvatar.isDuckProfile(" CLARKHILARIO.BREAD@GMAIL.COM "));
        assertFalse(UserAvatar.isDuckProfile("someone.else@example.com"));
        assertFalse(UserAvatar.isDuckProfile(null));
    }

    @Test
    void loadsTheDuckImageForClark() {
        UserAvatar avatar = new UserAvatar(38, 36, false);
        assertDoesNotThrow(() -> avatar.setProfile("Clark Hilario", "clarkhilario.bread@gmail.com"));
    }
}
