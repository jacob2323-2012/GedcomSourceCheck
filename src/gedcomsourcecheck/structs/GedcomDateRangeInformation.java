/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package gedcomsourcecheck.structs;

import java.util.Date;

/**
 *
 * @author Andreas
 */
public class GedcomDateRangeInformation {

        public Date date;
        public GedcomDateInformation dateInfo1;
        public GedcomDateInformation dateInfo2;
        public String formattedString;

        public GedcomDateRangeInformation(
                Date pDate,
                GedcomDateInformation pDateInfo1,
                GedcomDateInformation pDateInfo2,
                String pFormattedString) {
            date = pDate;
            dateInfo1 = pDateInfo1;
            dateInfo2 = pDateInfo2;
            formattedString = pFormattedString;
        }
    }
