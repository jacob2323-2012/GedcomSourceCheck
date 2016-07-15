/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package gedcomsourcecheck.structs;

import java.text.DateFormat;
import java.util.Date;

/**
 *
 * @author Andreas
 */
public class GedcomDateInformation {
     public Date date;
        public DateFormat dataFormat;
        public Boolean dateIsValid;

        public GedcomDateInformation(Date pDate, DateFormat pFormat, Boolean pDateIsValid) {
            this.date = pDate;
            this.dataFormat = pFormat;
            this.dateIsValid = pDateIsValid;
        }
}
