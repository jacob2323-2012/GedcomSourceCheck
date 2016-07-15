/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package gedcomsourcecheck;

import gedcomsourcecheck.structs.GedcomDateRangeInformation;
import java.util.Hashtable;
import java.util.Locale;
import java.util.Set;
import org.gedcom4j.model.StringWithCustomTags;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Andreas
 */
public class GedcomDateParserTest {

    public GedcomDateParserTest() {
    }

    @BeforeClass
    public static void setUpClass() {
    }

    @AfterClass
    public static void tearDownClass() {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    /**
     * Test of parseGedcomDateRange method, of class GedcomDateParser.
     */
    @Test
    public void testParseGedcomDateRangeDe() {
        System.out.println("parseGedcomDateRange with German Locale..");
        Hashtable<String, String> testVals = new Hashtable<>();
        GedcomDateParser instance = new GedcomDateParser(Locale.GERMAN);

        testVals.put("ABT 2015", "um 2015");
        testVals.put("AFT 30 MAY 2016", "nach 30.05.2016");
        testVals.put("BEF 4 JUN 2016", "vor 04.06.2016");
        testVals.put("BET 6 JUN 2016 AND 7 JUN 2017", "zw. 06.06.2016 und 07.06.2017");
        testVals.put("EST 8 JUN 2016", "08.06.2016 (geschÃ¤tzt)");
        testVals.put("CAL 9 JUN 2016", "09.06.2016 (errechnet)");
        testVals.put("10 JUN 2016", "10.06.2016");
        testVals.put("FROM 11 JUN 2016", "ab 11.06.2016");
        testVals.put("TO 12 JUN 2016", "bis 12.06.2016");
        testVals.put("FROM 14 JUN 2016 TO 15 JUN 2017", "von 14.06.2016 bis 15.06.2017");

        Set<String> keys = testVals.keySet();
        for (String key : keys) {
            String expVal = testVals.get(key);

            StringWithCustomTags pGedcomDate = new StringWithCustomTags(key);

            GedcomDateRangeInformation result = instance.parseGedcomDateRange(pGedcomDate);
            System.out.println("   test '" + key + "' -> '" + result.formattedString + "'");
            assertEquals(expVal, result.formattedString);
        }
    }

    /**
     * Test of parseGedcomDateRange method, of class GedcomDateParser.
     */
    @Test
    public void testParseGedcomDateRangeEn() {
        System.out.println("parseGedcomDateRange with English Locale..");
        Hashtable<String, String> testVals = new Hashtable<>();
        GedcomDateParser instance = new GedcomDateParser(Locale.ENGLISH);

        testVals.put("ABT 2015", "about 2015");
        testVals.put("AFT 30 MAY 2016", "after May 30, 2016");
        testVals.put("BEF 4 JUN 2016", "before Jun 4, 2016");
        testVals.put("BET 6 JUN 2016 AND 7 JUN 2017", "between Jun 6, 2016 and Jun 7, 2017");
        testVals.put("EST 8 JUN 2016", "Jun 8, 2016 (estimated)");
        testVals.put("CAL 9 JUN 2016", "Jun 9, 2016 (calculated)");
        testVals.put("10 JUN 2016", "Jun 10, 2016");
        testVals.put("FROM 11 JUN 2016", "from Jun 11, 2016");
        testVals.put("TO 12 JUN 2016", "up to Jun 12, 2016");
        testVals.put("FROM 14 JUN 2016 TO 15 JUN 2017", "from Jun 14, 2016 to Jun 15, 2017");

        Set<String> keys = testVals.keySet();
        for (String key : keys) {
            String expVal = testVals.get(key);

            StringWithCustomTags pGedcomDate = new StringWithCustomTags(key);

            GedcomDateRangeInformation result = instance.parseGedcomDateRange(pGedcomDate);
            System.out.println("   test '" + key + "' -> '" + result.formattedString + "'");
            assertEquals(expVal, result.formattedString);
        }
    }

    @Test
    public void testIncompleteDates() {
        GedcomDateParser instance = new GedcomDateParser(Locale.GERMAN);

        StringWithCustomTags pGedcomDate = new StringWithCustomTags("ABT 10 MAR");
        String expVal = "um 10 Mrz";

        GedcomDateRangeInformation result = instance.parseGedcomDateRange(pGedcomDate);
        assertEquals(expVal, result.formattedString);
        assertNull(result.date);
    }

}
