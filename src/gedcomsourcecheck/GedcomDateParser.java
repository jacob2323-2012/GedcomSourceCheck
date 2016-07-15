/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package gedcomsourcecheck;

import gedcomsourcecheck.structs.GedcomDateInformation;
import gedcomsourcecheck.structs.GedcomDateRangeInformation;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import org.gedcom4j.model.StringWithCustomTags;
import util.Util;
import static util.Util.localiseString;

/**
 *
 * @author Andreas
 */
public class GedcomDateParser {

    private Locale _locale;

    private Locale getLocale() {
        return _locale;
    }

    public GedcomDateParser(Locale pLocale) {
        _locale = pLocale;
    }

    /**
     * Concatinate the part of the JSON String containing the sources separated
     * by the certainty.
     *
     * @param pGedcomDate the string representing a date in gedcom
     * @return the parsed date
     */
    public GedcomDateRangeInformation parseGedcomDateRange(StringWithCustomTags pGedcomDate) {
        GedcomDateRangeInformation retVal = null;

        if (pGedcomDate != null && pGedcomDate.value.length() > 0) {

            String inStringDate = pGedcomDate.value;
            String outStringDate1 = inStringDate;
            String outStringDate2 = null;
            String descriptivePart = "{0}";

            if (inStringDate.contains("BET")) {
                String temp = inStringDate.replace("BET ", "");
                outStringDate1 = temp.substring(0, temp.indexOf(" AND "));
                outStringDate2 = temp.substring(temp.indexOf(" AND ") + 5);
                descriptivePart = Util.localiseString("DATE_BET_AND", this.getLocale());

            } else if (inStringDate.contains(" TO ")) {
                String temp = inStringDate.replace("FROM ", "");
                outStringDate1 = temp.substring(0, temp.indexOf(" TO "));
                outStringDate2 = temp.substring(temp.indexOf(" TO ") + 4);
                descriptivePart = Util.localiseString("DATE_FROM_TO", this.getLocale());

            } else if (inStringDate.contains("ABT")) {
                outStringDate1 = inStringDate.replace("ABT ", "");
                descriptivePart = Util.localiseString("DATE_ABT", this.getLocale());

            } else if (inStringDate.contains("BEF")) {
                outStringDate1 = inStringDate.replace("BEF ", "");
                descriptivePart = Util.localiseString("DATE_BEF", this.getLocale());

            } else if (inStringDate.contains("CAL")) {
                outStringDate1 = inStringDate.replace("CAL ", "");
                descriptivePart = Util.localiseString("DATE_CAL", this.getLocale());

            } else if (inStringDate.contains("TO")) {
                outStringDate1 = inStringDate.replace("TO ", "");
                descriptivePart = Util.localiseString("DATE_TO", this.getLocale());

            } else if (inStringDate.contains("EST")) {
                outStringDate1 = inStringDate.replace("EST ", "");
                descriptivePart = Util.localiseString("DATE_EST", this.getLocale());

            } else if (inStringDate.contains("AFT")) {
                outStringDate1 = inStringDate.replace("AFT ", "");
                descriptivePart = Util.localiseString("DATE_AFT", this.getLocale());

            } else if (inStringDate.contains("FROM")) {
                outStringDate1 = inStringDate.replace("FROM ", "");
                descriptivePart = Util.localiseString("DATE_FROM", this.getLocale());
            }

            GedcomDateInformation dateInfo1 = parseGedcomDate(outStringDate1);
            GedcomDateInformation dateInfo2;

            if (outStringDate2 != null) {
                dateInfo2 = parseGedcomDate(outStringDate2);
            } else {
                dateInfo2 = dateInfo1;
            }

            String dateString1 = dateInfo1.dataFormat.format(dateInfo1.date);
            String dateString2 = dateInfo2.dataFormat.format(dateInfo2.date);

            String aFormattedDateRange = MessageFormat.format(descriptivePart, dateString1, dateString2);

            retVal = new GedcomDateRangeInformation(
                    dateInfo1.dateIsValid ? dateInfo1.date : null,
                    dateInfo1,
                    dateInfo2,
                    aFormattedDateRange
            );

        }
        return retVal;
    }

    private GedcomDateInformation parseGedcomDate(String pEnglishDate) {
        Date retDate = null;
        DateFormat retFormat = null;
        Boolean dateIsValid = true;

        java.util.Locale loc = new java.util.Locale("en");
        DateFormat formatter0 = new SimpleDateFormat("dd MMM yyyy", loc);
        DateFormat formatter1 = new SimpleDateFormat("dd MMM", loc);
        DateFormat formatter2 = new SimpleDateFormat("yyyy");
        DateFormat formatter3 = new SimpleDateFormat("MMM yyyy", loc);

        if (pEnglishDate != null) {

            try {
                retDate = formatter0.parse(pEnglishDate);
                retFormat = DateFormat.getDateInstance(DateFormat.MEDIUM, this.getLocale());
            } catch (java.text.ParseException e0) {
                try {
                    formatter1.parse(pEnglishDate);
                    // as there is no year, we do not treat this information as a date
                    dateIsValid = false;
                    retDate = formatter1.parse(pEnglishDate);
                    retFormat = new SimpleDateFormat("dd MMM", this.getLocale());
                } catch (java.text.ParseException e1) {
                    try {
                        retDate = formatter2.parse(pEnglishDate);
                        retFormat = new SimpleDateFormat("yyyy", this.getLocale());
                    } catch (java.text.ParseException e2) {
                        try {
                            retDate = formatter3.parse(pEnglishDate);
                            retFormat = new SimpleDateFormat("MMM yyyy", this.getLocale());
                        } catch (java.text.ParseException e3) {
                            System.err.println("Non of the formatters was able to parse the following date: " + pEnglishDate);
                        }

                    }
                }
            }
        }
        return new GedcomDateInformation(retDate, retFormat, dateIsValid);
    }

}
