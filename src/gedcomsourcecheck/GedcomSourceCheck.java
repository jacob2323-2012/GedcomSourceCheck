/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package gedcomsourcecheck;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.util.List;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import java.text.SimpleDateFormat;
import java.text.ParseException;
import java.util.Date;
import java.util.Locale;
import java.util.ResourceBundle;

import org.gedcom4j.model.*;
import org.gedcom4j.parser.GedcomParser;
import org.gedcom4j.parser.GedcomParserException;

/**
 * Creates simple Ahnentafal HTML report from a GEDCOM.
 *
 * @author Bill Sundstrom
 * @author frizbog1
 */
public class GedcomSourceCheck {

    private Locale _locale;

    public Locale getLocale() {
        return _locale;
    }

    /**
     * The report buffer
     */
    public static StringBuilder reportBuf = new StringBuilder("");

    private static final HashMap<String, String> sexToChildrenMapping;

    static {
        sexToChildrenMapping = new HashMap<String, String>();
        sexToChildrenMapping.put("M", "des Sohns");
        sexToChildrenMapping.put("F", "der Tochter");
    }

    private static final HashMap<String, String> sexToParentsMapping;

    static {
        sexToParentsMapping = new HashMap<String, String>();
        sexToParentsMapping.put("M", "des Vaters");
        sexToParentsMapping.put("F", "der Mutter");
    }

    private static final HashMap<String, String> sexToSilbingsMapping;

    static {
        sexToSilbingsMapping = new HashMap<String, String>();
        sexToSilbingsMapping.put("M", "des Bruders");
        sexToSilbingsMapping.put("F", "der Schwester");
    }

    private static final HashMap<String, HashMap> relevanceMapping;
    private static final HashMap<String, Object> configOptions;
    private static final HashMap<String, String> salutationMapping;
    private static final ArrayList<IndividualEventType> possibleEventTypes;

    static {

        salutationMapping = new HashMap<>();
        salutationMapping.put("M", "");
        salutationMapping.put("F", "");

        possibleEventTypes = new ArrayList<>();
        possibleEventTypes.add(IndividualEventType.DEATH);

        configOptions = new HashMap<>();
        configOptions.put("salutation", salutationMapping);
        configOptions.put("possibleEventTypes", possibleEventTypes);

        relevanceMapping = new HashMap<>();
        relevanceMapping.put("direct", configOptions);

        salutationMapping.clear();
        salutationMapping.put("M", "des Sohnes");
        salutationMapping.put("F", "der Tochter");

        possibleEventTypes.clear();
        possibleEventTypes.add(IndividualEventType.BIRTH);

        configOptions.clear();
        configOptions.put("salutation", salutationMapping.clone());
        configOptions.put("possibleEventTypes", possibleEventTypes.clone());

        relevanceMapping.put("fams_children", configOptions);

    }

    public GedcomSourceCheck(Locale pLocale) {
        _locale = pLocale;
    }

    /**
     * The main method
     *
     * @param path
     * @throws GedcomParserException if the file can't be parsed
     * @throws IOException if the file can't be read
     */
    public String writeJson(BufferedInputStream stream) throws IOException, GedcomParserException, ParseException {

        // Load the gedcom file
        GedcomParser parser = new GedcomParser();
        parser.load(stream);

        // Stop if there were parser errors
        if (!parser.errors.isEmpty()) {
            for (String s : parser.errors) {
                System.err.println("Parser Errors: " + s);
            }
            return null;
        }
        Gedcom gedcom = parser.gedcom;

        ///
        System.out.println("AT start");

        String header = "data: [\n";
        reportBuf.append(header);

        for (Individual indi : gedcom.individuals.values()) {
            Date indiDateOfBirth = parseGedcomDateWithAdditions(new StringWithCustomTags("01 Jan 1200"));
            Date indiDateOfDeath = new Date();

            String name = "{formattedName: '" + indi.formattedName() + "', ";
            String aBasePart = name + "relevance:'direct', ";

            for (IndividualEvent ievent : indi.events) {
                Date eventDate = parseGedcomDateWithAdditions(ievent.date);
                switch (ievent.type) {
                    case BIRTH:
                        if (eventDate != null) {
                            indiDateOfBirth = eventDate;
                        }
                        break;
                    case DEATH:
                        if (eventDate != null) {
                            indiDateOfDeath = eventDate;
                        }
                        break;
                }
            }

            printOutFactsOfIndividual(indi, name, "direct", indiDateOfBirth, indiDateOfDeath);

            for (PersonalName pName : indi.names) {
                reportBuf.append(aBasePart + concatDatePart(null, indiDateOfBirth) + "factType: '" + localiseString("NAME") + "', factDescription: '" + pName.givenName + " " + pName.surname + "', " + concatSourcesJsonPart(pName.citations) + "},\n");
            }

            for (FamilySpouse fams : indi.familiesWhereSpouse) {
                Family fam = fams.family;

                // print out the childrens individual events
                aBasePart = name + "relevance:'fams_children', ";
                for (Individual child : fam.children) {

                    //printOutFactsOfIndividual(child, name, "fams_children", indiDateOfBirth, indiDateOfDeath);
                    for (IndividualEvent cevent : child.events) {
                        String type = (cevent.type == IndividualEventType.EVENT) ? cevent.subType.value : cevent.type.toString();
                        String description = "";
                        switch (cevent.type) {
                            case BIRTH:
                                description = "Geburt " + sexToChildrenMapping.get(child.sex.value) + " " + child.names.get(0).givenName + concatPlaceePart(cevent.place);
                                break;
                            case DEATH:
                                description = "Tod " + sexToChildrenMapping.get(child.sex.value) + " " + child.names.get(0).givenName + concatPlaceePart(cevent.place);
                                break;

                        }
                        Date eventDate = parseGedcomDateWithAdditions(cevent.date);
                        if (eventDate != null && indiDateOfDeath != null && indiDateOfBirth != null && eventDate.before(indiDateOfDeath)) {
                            reportBuf.append(aBasePart + concatDatePart(cevent.date, indiDateOfBirth) + "factType: '" + type + "', factDescription: '" + description + "', " + concatSourcesJsonPart(cevent.citations) + "},\n"
                            );
                        }
                    }
                }
            }

            for (FamilyChild famc : indi.familiesWhereChild) {
                Family fam = famc.family;

                // print out the parents individual events
                aBasePart = name + "relevance:'fams_children', ";
                ArrayList<Individual> parents = new ArrayList<Individual>();
                parents.add(fam.wife);
                parents.add(fam.husband);

                for (Individual parent : parents) {
                    if (parent != null) {
                        for (IndividualEvent pevent : parent.events) {
                            String type = (pevent.type == IndividualEventType.EVENT) ? pevent.subType.value : pevent.type.toString();
                            String description = "";
                            switch (pevent.type) {
                                case BIRTH:
                                    description = "Geburt " + sexToParentsMapping.get(parent.sex.value) + concatPlaceePart(pevent.place);
                                    break;
                                case DEATH:
                                    description = "Tod " + sexToParentsMapping.get(parent.sex.value) + concatPlaceePart(pevent.place);
                                    break;

                            }
                            Date eventDate = parseGedcomDateWithAdditions(pevent.date);
                            if (eventDate != null && indiDateOfBirth != null && eventDate.after(indiDateOfBirth)) {
                                reportBuf.append(aBasePart + concatDatePart(pevent.date, indiDateOfBirth)
                                        + "factType: '" + type
                                        + "', factDescription: '" + description + "', "
                                        + concatSourcesJsonPart(pevent.citations) + "},\n");
                            }
                        }
                    }
                }

                // print out the siblings individual events
                aBasePart = name + "relevance:'fams_children', ";
                ArrayList<Individual> siblings = new ArrayList<Individual>();
                for (Individual child : fam.children) {
                    // add all children but not the proband
                    if (child != indi) {
                        siblings.add(child);
                    }
                }

                for (Individual silbing : siblings) {
                    for (IndividualEvent sevent : silbing.events) {
                        String type = (sevent.type == IndividualEventType.EVENT) ? sevent.subType.value : sevent.type.toString();
                        String description = "";
                        switch (sevent.type) {
                            case BIRTH:
                                description = "Geburt " + sexToSilbingsMapping.get(silbing.sex.value) + " " + silbing.names.get(0).givenName + concatPlaceePart(sevent.place);
                                break;
                            case DEATH:
                                description = "Tod " + sexToSilbingsMapping.get(silbing.sex.value) + " " + silbing.names.get(0).givenName + concatPlaceePart(sevent.place);
                                break;

                        }
                        Date eventDate = parseGedcomDateWithAdditions(sevent.date);
                        if (indiDateOfBirth != null && indiDateOfDeath != null && eventDate != null && eventDate.after(indiDateOfBirth) && eventDate.before(indiDateOfDeath)) {
                            reportBuf.append(aBasePart + concatDatePart(sevent.date, indiDateOfBirth) + "factType: '" + type + "', factDescription: '" + description + "', " + concatSourcesJsonPart(sevent.citations) + "},\n");
                        }
                    }
                }
            }

        }

        String footer = "]";
        reportBuf.append(footer);

        return reportBuf.toString();
    }

    /**
     * Concatinate the part of the JSON String containing the date and age
     * informations.
     *
     * @param (Place} pPlace
     * @param pIndiDateOfBirth
     * @return the concatinated json string
     */
    private String concatPlaceePart(Place pPlace) {
        String retVal = "";

        if (pPlace != null && pPlace.placeName != null && pPlace.placeName.length() > 0) {
            retVal = " in " + pPlace.placeName;
        }

        return retVal;
    }

    /**
     * Prints out attributes and events of the individual and its own family
     * dependent on the relevance and the min- and max date.
     *
     * Description is concatinated out of optional place information and the
     * person's titel determined by the given relevance.
     *
     * @param {Individual} pIndi
     * @param (String} pBasePart
     * @param {String} pRelevance
     * @param {Date} pMinDate
     * @param {Date} pMaxDate
     */
    private void printOutFactsOfIndividual(Individual pIndi, String pBasePart,
            String pRelevance, Date pMinDate, Date pMaxDate) {

        String aBasePart = pBasePart + "relevance:'" + pRelevance + "', ";

        // HashMap<String, Object> config = relevanceMapping.get(pRelevance);
        for (IndividualAttribute attrib : pIndi.attributes) {
            printOutFactDependentOnDate(attrib.date, pMinDate, pMaxDate,
                    aBasePart + concatDatePart(attrib.date, pMinDate)
                    + "factType: '" + localiseString(attrib.type.toString())
                    + "', factDescription: '" + attrib.description + "', "
                    + concatSourcesJsonPart(attrib.citations) + "},\n");
        }

        for (IndividualEvent ievent : pIndi.events) {
            String type = (ievent.type == IndividualEventType.EVENT) ? ievent.subType.value : ievent.type.toString();
            String description = "";
            switch (ievent.type) {
                case BIRTH:
                    description = "Geburt" + concatPlaceePart(ievent.place);
                    //HashMap<String, String> mySalutationMapping = (HashMap<String, String>) config.get("salutation");
                    //description = "Geburt " + mySalutationMapping.get(pIndi.sex.value) + " " + pIndi.names.get(0).givenName + concatPlaceePart(ievent.place);
                    break;
                case DEATH:
                    description = "Tod" + concatPlaceePart(ievent.place);
                    break;
            }

            printOutFactDependentOnDate(ievent.date, pMinDate, pMaxDate,
                    aBasePart + concatDatePart(ievent.date, pMinDate)
                    + "factType: '" + localiseString(type)
                    + "', factDescription: '" + description + "', "
                    + concatSourcesJsonPart(ievent.citations) + "},\n");
        }

        for (FamilySpouse fams : pIndi.familiesWhereSpouse) {
            Family fam = fams.family;

            // print out family events
            for (FamilyEvent fevent : fam.events) {
                String type = (fevent.type == FamilyEventType.EVENT) ? fevent.subType.value : fevent.type.toString();
                String place = (fevent.place != null) ? fevent.place.placeName : "";
                printOutFactDependentOnDate(fevent.date, pMinDate, pMaxDate,
                        aBasePart + concatDatePart(fevent.date, pMinDate)
                        + "factType: '" + localiseString(type)
                        + "', factDescription: 'Heirat in " + place + "', "
                        + concatSourcesJsonPart(fevent.citations) + "},\n");
            }
        }
    }

    private String localiseString(String aKey) {

        ResourceBundle labels = ResourceBundle.getBundle("util.LocalisedText", this.getLocale());
        String retVal;

        try {
            retVal = labels.getString(aKey);
        } catch (java.util.MissingResourceException e) {
            retVal = aKey;
        }
        return retVal;
    }

    /**
     * Prints out one attribute/event
     *
     * @param {StringWithCustomTags} pGedcomEventDate
     * @param {Date} pMinDate
     * @param {Date} pMaxDate
     * @param {String} pJsonStringToPrint
     */
    private void printOutFactDependentOnDate(StringWithCustomTags pGedcomEventDate,
            Date pMinDate, Date pMaxDate, String pJsonStringToPrint) {

        try {
            Date eventDate = parseGedcomDateWithAdditions(pGedcomEventDate);
            if (eventDate.equals(pMinDate) || eventDate.equals(pMaxDate) || (eventDate.after(pMinDate) && eventDate.before(pMaxDate))) {
                reportBuf.append(pJsonStringToPrint);
            }
        } catch (java.text.ParseException | java.lang.NullPointerException e) {
            reportBuf.append(pJsonStringToPrint);
        }
    }

    /**
     * Concatinate the part of the JSON String containing the sources separated
     * by the certainty.
     *
     * @param pCitations the list of citations to process
     * @return the concatinated string
     */
    private String concatSourcesJsonPart(List<AbstractCitation> pCitations) {

        int primaryCount = 0;
        String primaryDetails = "";
        int secondaryCount = 0;
        String secondaryDetails = "";
        int questionableCount = 0;
        String questionableDetails = "";
        int unreliableCount = 0;
        String unreliableDetails = "";
        int unclassifiedCount = 0;
        String unclassifiedDetails = "";

        for (AbstractCitation citation : pCitations) {
            CitationWithSource citationWS = (CitationWithSource) citation;

            if (citationWS.certainty != null) {
                if (citationWS.certainty.value.equals("3")) {
                    primaryCount++;
                    primaryDetails += extractDetailsFromSource(citationWS);
                } else if (citationWS.certainty.value.equals("2")) {
                    secondaryCount++;
                    secondaryDetails += extractDetailsFromSource(citationWS);
                } else if (citationWS.certainty.value.equals("1")) {
                    questionableCount++;
                    questionableDetails += extractDetailsFromSource(citationWS);
                } else if (citationWS.certainty.value.equals("4")) {
                    unreliableCount++;
                    unreliableDetails += extractDetailsFromSource(citationWS);
                }
            } else {
                unclassifiedCount++;
                unclassifiedDetails += extractDetailsFromSource(citationWS);
            }
        }
        return "noOfPrimarySources: " + primaryCount + ",detailsForPrimarySources: '" + primaryDetails
                + "' ,noOfSecondarySources: " + secondaryCount + ",detailsForSecondarySources: '" + secondaryDetails
                + "', noOfQuestionableSources: " + questionableCount + ",detailsForQuestionableSources: '" + questionableDetails
                + "', noOfUnreliableSources: " + unreliableCount + ",detailsForUnreliableSources: '" + unreliableDetails
                + "', noOfUnclassifiedSources: " + unclassifiedCount + ",detailsForUnclassifiedSources: '" + unclassifiedDetails + "'";
    }

    /**
     * Concatinate the part of the JSON String containing the sources separated
     * by the certainty.
     *
     * @param pCitations the list of citations to process
     * @return the concatinated string
     */
    private String extractDetailsFromSource(CitationWithSource pCitation) {
        String retVal;
        StringWithCustomTags wherePart = pCitation.whereInSource;
        String titlePart = pCitation.source.title.get(0);

        if (wherePart != null && wherePart.value.length() > 0) {
            retVal = wherePart.value + " in --" + titlePart + "--<br/>";
        } else {
            retVal = titlePart;
        }

        return retVal;
    }

    /**
     * Concatinate the part of the JSON String containing the date and age
     * informations.
     *
     * @param pGedcomDate the string representing a date in gedcom
     * @param pIndiDateOfBirth
     * @return the concatinated json string
     */
    private String concatDatePart(StringWithCustomTags pGedcomDate, Date pIndiDateOfBirth) {

        // quick exit if gedcomdate is empty
        if (pGedcomDate == null || pGedcomDate.value.length() < 1) {
            return "factDate: '', factInternalDate:'01.01.1200', age: '', ";
        }

        Date eventDate;
        try {
            eventDate = parseGedcomDateWithAdditions(pGedcomDate);
            DateFormat outptFormatter = new SimpleDateFormat("dd.MM.yyyy");

            long diffInMillies = eventDate.getTime() - pIndiDateOfBirth.getTime();
            long diffInDays = TimeUnit.DAYS.convert(diffInMillies, TimeUnit.MILLISECONDS);
            long age = diffInDays / 365;

            String formattedDate = formatDate(pGedcomDate, this.getLocale());

            return "factDate: '" + formattedDate + "', factInternalDate:'" + outptFormatter.format(eventDate) + "', age: '" + age + "', ";
        } catch (ParseException e) {
            return "factDate: '" + pGedcomDate.value + "', factInternalDate:'01.01.1200', age: '', ";
        }
    }

    private String formatDate(StringWithCustomTags pGedcomDate, Locale locale) {

        String retVal;

        String inStringDate = pGedcomDate.value;
        String outStringDate1 = inStringDate;
        String outStringDate2 = null;
        String descriptivePart = "{0}";
        ResourceBundle labels = ResourceBundle.getBundle("util.LocalisedText", locale);

        if (inStringDate.contains("BET")) {
            String temp = inStringDate.replace("BET ", "");
            outStringDate1 = temp.substring(0, temp.indexOf(" AND "));
            outStringDate2 = temp.substring(temp.indexOf(" AND ") + 5);
            descriptivePart = labels.getString("DATE_BET_AND");

        } else if (inStringDate.contains(" TO ")) {
            String temp = inStringDate.replace("FROM ", "");
            outStringDate1 = temp.substring(0, temp.indexOf(" TO "));
            outStringDate2 = temp.substring(temp.indexOf(" TO ") + 4);
            descriptivePart = labels.getString("DATE_FROM_TO");

        } else if (inStringDate.contains("ABT")) {
            outStringDate1 = inStringDate.replace("ABT ", "");
            descriptivePart = labels.getString("DATE_ABT");
        } else if (inStringDate.contains("BEF")) {
            outStringDate1 = inStringDate.replace("BEF ", "");
            descriptivePart = labels.getString("DATE_BEF");
        } else if (inStringDate.contains("CAL")) {
            outStringDate1 = inStringDate.replace("CAL ", "");
            descriptivePart = labels.getString("DATE_CAL");
        } else if (inStringDate.contains("TO")) {
            outStringDate1 = inStringDate.replace("TO ", "");
            descriptivePart = labels.getString("DATE_TO");
        } else if (inStringDate.contains("EST")) {
            outStringDate1 = inStringDate.replace("EST ", "");
            descriptivePart = labels.getString("DATE_EST");
        } else if (inStringDate.contains("AFT")) {
            outStringDate1 = inStringDate.replace("AFT ", "");
            descriptivePart = labels.getString("DATE_AFT");
        } else if (inStringDate.contains("FROM")) {
            outStringDate1 = inStringDate.replace("FROM ", "");
            descriptivePart = labels.getString("DATE_FROM");
        }

        DateFormat dfMedium = DateFormat.getDateInstance(DateFormat.MEDIUM, locale);
        DateFormat dfYear = new SimpleDateFormat("MMMM", new Locale("en"));
        DateFormat dfMonth = new SimpleDateFormat("M", locale);

        GedcomDateInformation dateInfo1 = parseGedcomDate(outStringDate1, locale);
        GedcomDateInformation dateInfo2;

        System.out.println("formatGedcomDAte: " + outStringDate1);

        if (outStringDate2 != null) {
            dateInfo2 = parseGedcomDate(outStringDate2, locale);
        } else {
            dateInfo2 = dateInfo1;
        }

        String dateString1 = dateInfo1.dataFormat.format(dateInfo1.date);
        String dateString2 = dateInfo2.dataFormat.format(dateInfo2.date);

        retVal = MessageFormat.format(descriptivePart, dateString1, dateString2);

        return retVal;
    }

    /**
     * Concatinate the part of the JSON String containing the sources separated
     * by the certainty.
     *
     * @param pGedcomDate the string representing a date in gedcom
     * @return the parsed date
     */
    private Date parseGedcomDateWithAdditions(StringWithCustomTags pGedcomDate) throws ParseException {
        System.out.println(".." + pGedcomDate);
        Date retVal = null;

        if (pGedcomDate != null) {
            String inStringDate = pGedcomDate.value;
            String outStringDate = inStringDate;

            if (inStringDate.contains("BET")) {
                outStringDate = inStringDate.replace("BET ", "");
                outStringDate = outStringDate.substring(0, outStringDate.indexOf(" AND "));
            } else if (inStringDate.contains(" TO ")) {
                outStringDate = inStringDate.replace("FROM ", "");
                outStringDate = outStringDate.substring(0, outStringDate.indexOf(" TO "));
            } else if (inStringDate.contains("ABT")) {
                outStringDate = inStringDate.replace("ABT ", "");
            } else if (inStringDate.contains("BEF")) {
                outStringDate = inStringDate.replace("BEF ", "");
            } else if (inStringDate.contains("CAL")) {
                outStringDate = inStringDate.replace("CAL ", "");
            } else if (inStringDate.contains("TO")) {
                outStringDate = inStringDate.replace("TO ", "");
            } else if (inStringDate.contains("EST")) {
                outStringDate = inStringDate.replace("EST ", "");
            } else if (inStringDate.contains("AFT")) {
                outStringDate = inStringDate.replace("AFT ", "");
            } else if (inStringDate.contains("FROM")) {
                outStringDate = inStringDate.replace("FROM ", "");
            }

            retVal = parseGedcomDate(outStringDate, this.getLocale()).date;

        }

        return retVal;
    }

    private GedcomDateInformation parseGedcomDate(String pEnglishDate, Locale targetLocale) {
        Date retDate = null;
        DateFormat retFormat = null;

        java.util.Locale loc = new java.util.Locale("en");
        DateFormat formatter = new SimpleDateFormat("dd MMM yyyy", loc);
        DateFormat formatter2 = new SimpleDateFormat("yyyy");
        DateFormat formatter3 = new SimpleDateFormat("MMM yyyy", loc);

        if (pEnglishDate != null) {

            try {
                retDate = formatter.parse(pEnglishDate);
                retFormat = DateFormat.getDateInstance(DateFormat.MEDIUM, targetLocale);
            } catch (java.text.ParseException e1) {
                try {
                    retDate = formatter2.parse(pEnglishDate);
                    retFormat = new SimpleDateFormat("yyyy", targetLocale);
                } catch (java.text.ParseException e2) {
                    try {
                        retDate = formatter3.parse(pEnglishDate);
                        retFormat = new SimpleDateFormat("MMM yyyy", targetLocale);
                    } catch (java.text.ParseException e3) {
                        System.err.println("Non of the formatters was able to parse the following date: " + pEnglishDate);
                    }

                }
            }
        }
        return new GedcomDateInformation(retDate, retFormat);
    }

    private class GedcomDateInformation {

        public Date date;
        public DateFormat dataFormat;

        public GedcomDateInformation(Date pDate, DateFormat pFormat) {
            this.date = pDate;
            this.dataFormat = pFormat;
        }

    }

}
