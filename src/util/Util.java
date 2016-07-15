/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package util;

import java.util.Locale;
import java.util.ResourceBundle;

/**
 *
 * @author Andreas
 */
public class Util {
        public static String localiseString(String pKey, Locale pLocale) {

        ResourceBundle labels = ResourceBundle.getBundle("util.LocalisedText", pLocale);
        String retVal;

        try {
            retVal = labels.getString(pKey);
        } catch (java.util.MissingResourceException e) {
            retVal = pKey;
        }
        return retVal;
    }
}
