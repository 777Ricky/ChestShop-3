package com.Acrobot.ChestShop.Tests;

import com.Acrobot.Breeze.Utils.PriceUtil;
import com.Acrobot.ChestShop.Configuration.Properties;
import com.Acrobot.ChestShop.Events.PreShopCreationEvent;
import com.Acrobot.ChestShop.Events.SignValidationEvent;
import com.Acrobot.ChestShop.Listeners.PreShopCreation.PriceChecker;
import com.Acrobot.ChestShop.Listeners.SignParseListener;
import com.Acrobot.ChestShop.Signs.ChestShopSign;
import com.Acrobot.ChestShop.Signs.ShopSignColors;
import org.bukkit.ChatColor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class ShopSignColorsTest {
    @ParameterizedTest
    @ValueSource(strings = {"0", "1", "2", "3", "4", "5", "6", "7", "8", "9",
            "a", "b", "c", "d", "e", "f", "k", "l", "m", "n", "o", "r",
            "A", "B", "C", "D", "E", "F", "K", "L", "M", "N", "O", "R"})
    void translatesBukkitCodes(String code) {
        String[] original = {"&" + code + "Owner", "64", "B 10", "Diamond"};
        String[] translated = ShopSignColors.translate(original);

        assertEquals("\u00a7" + code.toLowerCase(java.util.Locale.ROOT) + "Owner", translated[0]);
        assertEquals("&" + code + "Owner", original[0]);
        assertNotSame(original, translated);
    }

    @Test
    void leavesPlainTextInvalidCodesAndExistingColorsAlone() {
        String[] original = {"Fish & Chips &z &", null, "B 10", color("&bDiamond")};
        assertArrayEquals(original, ShopSignColors.translate(original));
    }

    @ParameterizedTest
    @MethodSource("restoredLines")
    void preservesFormattingWithoutChangingCanonicalText(String original, String canonical, String expected) {
        String restored = ShopSignColors.restore(color(original), color(canonical));
        assertEquals(color(expected), restored);
        assertEquals(ChatColor.stripColor(color(canonical)), ChatColor.stripColor(restored));
    }

    static Stream<Arguments> restoredLines() {
        return Stream.of(
                Arguments.of("Owner", "Owner", "Owner"),
                Arguments.of("&aOw&bner&r", "Owner", "&aOw&bner&r"),
                Arguments.of(" &Aow&bner&r ", "Owner", "&aOw&bner&r"),
                Arguments.of("&e&l", "Owner", "&e&lOwner"),
                Arguments.of("&aSomeoneElse", "Owner", "&aOwner"),
                Arguments.of("&aSomeoneElse", "", ""),
                Arguments.of("&aOwner", null, null),
                Arguments.of("&aOwner", "&cOwner", "&a&cOwner"),
                Arguments.of("&aB 10:&cS 5", "B 10:S 5", "&aB 10:&cS 5"),
                Arguments.of(" &ab 10 : &cs 5 ", "B10:S5", "&aB10:&cS5"),
                Arguments.of("&aB 10.000:&cS 5.000", "B 10:S 5", "&aB 10:&cS 5"),
                Arguments.of("&a10", "B 10", "&aB 10"),
                Arguments.of("&bdiamond_sword", "Diamond Sword", "&bDiamond Sword"),
                Arguments.of("&bDiamond#az3:203", "Diamond#az3:203", "&bDiamond#az3:203"),
                Arguments.of("&b?", "&l?", "&b&l?"),
                Arguments.of("&b?", "Diamond", "&bDiamond"),
                Arguments.of("&e64", "Q 64 : C 128", "&eQ 64 : C 128"),
                Arguments.of("&eQ 64 : &aC 128", "Q 64 : C 64", "&eQ 64 :&a C 64"),
                Arguments.of("&eQ 64 : C 128", "64", "&e64")
        );
    }

    @Test
    void coloredGettersAndValidationMatchPlainSigns() {
        String[] lines = ShopSignColors.translate(new String[]{
                "&bOwner:az3&r", "&eQ 64 : &aC 128", "&aB 10:&cS 5", "&bDiamond#az3:203&r"
        });

        assertEquals("Owner:az3", ChestShopSign.getOwner(lines));
        assertEquals("Q 64 : C 128", ChestShopSign.getQuantityLine(lines));
        assertEquals(64, ChestShopSign.getQuantity(lines));
        assertEquals("B 10:S 5", ChestShopSign.getPrice(lines));
        assertEquals("Diamond#az3:203", ChestShopSign.getItem(lines));

        SignValidationEvent event = new SignValidationEvent(lines);
        new SignParseListener().onSignValidation(event);
        assertTrue(event.isValid());
    }

    @Test
    void coloredAdminShopNameStillIdentifiesAdminShop() {
        String[] lines = ShopSignColors.translate(new String[]{"&b" + Properties.ADMIN_SHOP_NAME, "1", "B 1", "Stone"});
        assertTrue(ChestShopSign.isAdminShop(lines));
    }

    @ParameterizedTest
    @ValueSource(strings = {"&e-1", "&e0", "&e0123", "&e64&z"})
    void coloringDoesNotMakeInvalidQuantitiesValid(String quantity) {
        SignValidationEvent event = new SignValidationEvent(ShopSignColors.translate(
                new String[]{"&bOwner", quantity, "&aB 10", "&bDiamond"}));
        new SignParseListener().onSignValidation(event);
        assertFalse(event.isValid());
    }

    @Test
    void normalizedColoredPricesKeepTheirValuesAndSeparateColors() {
        int precision = Properties.PRICE_PRECISION;
        try {
            Properties.PRICE_PRECISION = 2;
            String original = color("&ab 10.129:&cs 5.999");
            PreShopCreationEvent event = new PreShopCreationEvent(null, null,
                    new String[]{"Owner", "64", ChatColor.stripColor(original), "Diamond"});
            PriceChecker.onPreShopCreation(event);

            assertFalse(event.isCancelled());
            String display = ShopSignColors.restore(original, event.getSignLine(ChestShopSign.PRICE_LINE));
            assertEquals(color("&aB 10.12:&cS 5.99"), display);
            event.setSignLine(ChestShopSign.PRICE_LINE, display);
            assertEquals(new BigDecimal("10.12"), PriceUtil.getExactBuyPrice(ChestShopSign.getPrice(event.getSignLines())));
            assertEquals(new BigDecimal("5.99"), PriceUtil.getExactSellPrice(ChestShopSign.getPrice(event.getSignLines())));
        } finally {
            Properties.PRICE_PRECISION = precision;
        }
    }

    private static String color(String text) {
        return text == null ? null : ChatColor.translateAlternateColorCodes('&', text);
    }
}
