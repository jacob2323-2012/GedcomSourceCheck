/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package gedcomsourcecheck;

import java.io.IOException;
import java.util.List;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import java.text.SimpleDateFormat;
import java.text.DateFormat;
import java.text.ParseException;
import java.util.Date;

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

    /**
     * The main method
     *
     * @param path 
     * @throws GedcomParserException if the file can't be parsed
     * @throws IOException if the file can't be read
     */
    public static String writeJson(String path) throws IOException, GedcomParserException, ParseException {

        // Load the gedcom file
        GedcomParser parser = new GedcomParser();
        parser.load(path);

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

        String header = "Ext.define('Gedcom.store.SourcesByIndividual', {extend: 'Ext.data.Store',data: [\n";
        reportBuf.append(header);

        for (Individual indi : gedcom.individuals.values()) {
            Date indiDateOfBirth = parseGedcomDate(new StringWithCustomTags("01 Jan 1200"));
            Date indiDateOfDeath = new Date();

            String name = "{formattedName: '" + indi.formattedName() + "', ";
            String aBasePart = name + "relevance:'direct', ";

            for (IndividualEvent ievent : indi.events) {
                Date eventDate = parseGedcomDate(ievent.date);
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
                reportBuf.append(aBasePart + concatDatePart(null, indiDateOfBirth) + "factType: 'NAME', factDescription: '" + pName.givenName + " " + pName.surname + "', " + concatSourcesJsonPart(pName.citations) + "},\n");
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
                        Date eventDate = parseGedcomDate(cevent.date);
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
                            Date eventDate = parseGedcomDate(pevent.date);
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
                        Date eventDate = parseGedcomDate(sevent.date);
                        if (indiDateOfBirth != null && indiDateOfDeath != null && eventDate != null && eventDate.after(indiDateOfBirth) && eventDate.before(indiDateOfDeath)) {
                            reportBuf.append(aBasePart + concatDatePart(sevent.date, indiDateOfBirth) + "factType: '" + type + "', factDescription: '" + description + "', " + concatSourcesJsonPart(sevent.citations) + "},\n");
                        }
                    }
                }
            }

        }

        String footer = "]});";
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
    private static String concatPlaceePart(Place pPlace) {
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
    private static void printOutFactsOfIndividual(Individual pIndi, String pBasePart,
            String pRelevance, Date pMinDate, Date pMaxDate) {

        String aBasePart = pBasePart + "relevance:'" + pRelevance + "', ";

        // HashMap<String, Object> config = relevanceMapping.get(pRelevance);

        for (IndividualAttribute attrib : pIndi.attributes) {
            printOutFactDependentOnDate(attrib.date, pMinDate, pMaxDate,
                    aBasePart + concatDatePart(attrib.date, pMinDate)
                    + "factType: '" + attrib.type
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
                    + "factType: '" + type
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
                        + "factType: '" + type
                        + "', factDescription: 'Heirat in " + place + "', "
                        + concatSourcesJsonPart(fevent.citations) + "},\n");
            }
        }
    }

    /**
     * Prints out one attribute/event
     *
     * @param {StringWithCustomTags} pGedcomEventDate
     * @param {Date} pMinDate
     * @param {Date} pMaxDate
     * @param {String} pJsonStringToPrint
     */
    private static void printOutFactDependentOnDate(StringWithCustomTags pGedcomEventDate,
            Date pMinDate, Date pMaxDate, String pJsonStringToPrint) {

        try {
            Date eventDate = parseGedcomDate(pGedcomEventDate);
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
    private static String concatSourcesJsonPart(List<AbstractCitation> pCitations) {

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
    private static String extractDetailsFromSource(CitationWithSource pCitation) {
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
    private static String concatDatePart(StringWithCustomTags pGedcomDate, Date pIndiDateOfBirth) {

        // quick exit if gedcomdate is empty
        if (pGedcomDate == null || pGedcomDate.value.length() < 1) {
            return "factDate: '', factInternalDate:'01.01.1200', age: '', ";
        }

        Date eventDate;
        try {
            eventDate = parseGedcomDate(pGedcomDate);
            DateFormat outptFormatter = new SimpleDateFormat("dd.MM.yyyy");

            long diffInMillies = eventDate.getTime() - pIndiDateOfBirth.getTime();
            long diffInDays = TimeUnit.DAYS.convert(diffInMillies, TimeUnit.MILLISECONDS);
            long age = diffInDays / 365;

            return "factDate: '" + outptFormatter.format(eventDate) + "', factInternalDate:'" + outptFormatter.format(eventDate) + "', age: '" + age + "', ";
        } catch (ParseException e) {
            return "factDate: '" + pGedcomDate.value + "', factInternalDate:'01.01.1200', age: '', ";
        }
    }

    /**
     * Concatinate the part of the JSON String containing the sources separated
     * by the certainty.
     *
     * @param pGedcomDate the string representing a date in gedcom
     * @return the parsed date
     */
    private static Date parseGedcomDate(StringWithCustomTags pGedcomDate) throws ParseException {
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
            } else if (inStringDate.contains("AFT")) {
                outStringDate = inStringDate.replace("AFT ", "");
            } else if (inStringDate.contains("FROM")) {
                outStringDate = inStringDate.replace("FROM ", "");
            }

            pGedcomDate = new StringWithCustomTags(outStringDate);

            java.util.Locale loc = new java.util.Locale("en");
            DateFormat formatter = new SimpleDateFormat("dd MMM yyyy", loc);
            DateFormat formatter2 = new SimpleDateFormat("yyyy");
            DateFormat formatter3 = new SimpleDateFormat("MMM yyyy", loc);

            try {
                retVal = formatter.parse(pGedcomDate.toString());
            } catch (java.text.ParseException e1) {
                try {
                    retVal = formatter2.parse(pGedcomDate.toString());
                } catch (java.text.ParseException e2) {
                    retVal = formatter3.parse(pGedcomDate.toString());
                }
            }
        }

        return retVal;
    }

}
