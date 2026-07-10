package tech.guilhermekaua.spigotboot.core.spigot.utils;

import be.seeseemelk.mockbukkit.MockBukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class HeadUtilsTest {

    private static final String SKIN_URL = "http://textures.minecraft.net/texture/deadbeefcafe";

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    // --- getHeadByUrl: fail-safe behavior --------------------------------------------------------

    @Test
    void getHeadByUrl_null_returns_bare_player_head() {
        ItemStack head = HeadUtils.getHeadByUrl(null);
        assertEquals(Material.PLAYER_HEAD, head.getType());
    }

    @Test
    void getHeadByUrl_blank_returns_bare_player_head() {
        ItemStack head = HeadUtils.getHeadByUrl("   ");
        assertEquals(Material.PLAYER_HEAD, head.getType());
    }

    @Test
    void getHeadByUrl_valid_url_returns_player_head() {
        ItemStack head = HeadUtils.getHeadByUrl(SKIN_URL);
        assertEquals(Material.PLAYER_HEAD, head.getType());
    }

    // --- Tier 1: modern Bukkit PlayerProfile API (1.18.1+) --------------------------------------

    @Test
    void resolveProfileApi_binds_the_modern_profile_contract() {
        // Verifies the version-specific reflection targets against the real (paper-api 1.20.1) modern
        // profile API: wrong class names, method names, or signatures would surface here. MockBukkit's
        // server does not implement createPlayerProfile, so the end-to-end apply cannot run in tests.
        Optional<HeadUtils.ProfileApi> resolved = HeadUtils.resolveProfileApi();
        assertTrue(resolved.isPresent(), "paper-api 1.20.1 exposes the org.bukkit.profile API");

        HeadUtils.ProfileApi api = resolved.get();
        assertEquals("createPlayerProfile", api.createPlayerProfile.getName());
        assertArrayEquals(new Class<?>[]{UUID.class}, api.createPlayerProfile.getParameterTypes());
        assertEquals("getTextures", api.getTextures.getName());
        assertEquals("setSkin", api.setSkin.getName());
        assertArrayEquals(new Class<?>[]{URL.class}, api.setSkin.getParameterTypes());
        assertEquals("setOwnerProfile", api.setOwnerProfile.getName());
    }

    @Test
    void applyViaProfileApi_malformed_url_returns_false() {
        SkullMeta meta = mock(SkullMeta.class);

        assertFalse(HeadUtils.applyViaProfileApi(meta, "not a url"));
    }

    // --- Tier 2: legacy GameProfile texture property + field write ------------------------------

    @Test
    void encodeSkinTexture_encodes_minimal_texture_json() {
        String decoded = decode(HeadUtils.encodeSkinTexture(SKIN_URL));
        assertEquals("{\"textures\":{\"SKIN\":{\"url\":\"" + SKIN_URL + "\"}}}", decoded);
    }

    @Test
    void encodeSkinTexture_escapes_json_metacharacters() {
        String decoded = decode(HeadUtils.encodeSkinTexture("a\"b\\c"));
        assertEquals("{\"textures\":{\"SKIN\":{\"url\":\"a\\\"b\\\\c\"}}}", decoded);
    }

    @Test
    void createGameProfile_embeds_textures_property_with_the_encoded_skin() throws Exception {
        Object profile = HeadUtils.createGameProfile(SKIN_URL);
        assertNotNull(profile, "authlib GameProfile must be constructible on the test classpath");

        Object properties = profile.getClass().getMethod("getProperties").invoke(profile);
        Object textures = properties.getClass().getMethod("get", Object.class).invoke(properties, "textures");
        Collection<?> values = (Collection<?>) textures;
        assertEquals(1, values.size());

        Object property = values.iterator().next();
        Object value = property.getClass().getMethod("getValue").invoke(property);
        assertEquals(HeadUtils.encodeSkinTexture(SKIN_URL), value);
    }

    @Test
    void writeProfileField_writes_into_a_profile_field() {
        FakeSkullMeta meta = new FakeSkullMeta();
        Object marker = new Object();

        assertTrue(HeadUtils.writeProfileField(meta, marker));

        assertSame(marker, meta.profile);
    }

    @Test
    void writeProfileField_returns_false_when_class_has_no_profile_field() {
        assertFalse(HeadUtils.writeProfileField(new NoProfileField(), new Object()));
    }

    @Test
    void writeProfileField_returns_false_for_object_without_field() {
        assertFalse(HeadUtils.writeProfileField(new Object(), new Object()));
    }

    // --- isTextureUrl ----------------------------------------------------------------------------

    @Test
    void isTextureUrl_accepts_http_textures_minecraft_net() {
        assertTrue(HeadUtils.isTextureUrl(
                "http://textures.minecraft.net/texture/2fdd5f297d76d35257724ea722e06af12f847052225de6d4919c1aa773c25e5c"));
    }

    @Test
    void isTextureUrl_accepts_https_and_ignores_surrounding_whitespace() {
        assertTrue(HeadUtils.isTextureUrl(
                "  https://textures.minecraft.net/texture/deadbeefcafe  "));
    }

    @Test
    void isTextureUrl_is_case_insensitive_on_scheme_and_host() {
        assertTrue(HeadUtils.isTextureUrl(
                "HTTP://Textures.Minecraft.NET/texture/abc123"));
    }

    @Test
    void isTextureUrl_rejects_null_blank_player_name_and_incomplete_url() {
        assertFalse(HeadUtils.isTextureUrl(null));
        assertFalse(HeadUtils.isTextureUrl(""));
        assertFalse(HeadUtils.isTextureUrl("   "));
        assertFalse(HeadUtils.isTextureUrl("Notch"));
        assertFalse(HeadUtils.isTextureUrl("http://textures.minecraft.net/texture/"));
        assertFalse(HeadUtils.isTextureUrl("http://example.com/texture/abc"));
        assertFalse(HeadUtils.isTextureUrl("textures.minecraft.net/texture/abc"));
    }

    // --- existing head factories still work -----------------------------------------------------

    @Test
    void getHeadByName_sets_owner() {
        ItemStack head = HeadUtils.getHeadByName("Notch");
        assertEquals(Material.PLAYER_HEAD, head.getType());
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        assertEquals("Notch", meta.getOwner());
    }

    private static String decode(String base64) {
        return new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
    }

    /** Stand-in for a legacy CraftMetaSkull: exposes the private {@code profile} field the field path targets. */
    private static final class FakeSkullMeta {
        private Object profile;
    }

    private static final class NoProfileField {
    }
}
