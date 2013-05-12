package net.archiloque.googlehistory;

import au.com.bytecode.opencsv.CSVWriter;
import com.gargoylesoftware.htmlunit.ElementNotFoundException;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.*;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.io.IOException;
import java.io.Writer;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.*;
import org.w3c.dom.Node;

/**
 * Simple app that scrape your google history and make it available as csv
 */
public class GoogleHistoryApp {

    private final JFrame jFrame;

    private final JTextArea logArea;
    private final JTextArea mainTextArea;

    private final JLabel searchNumber = new JLabel();
    private final JLabel searchDate = new JLabel();
    private final CSVWriter csvWriter;
    private static final String APPLICATION_NAME = "Google History";

    private final ResourceBundle resourceBundle = PropertyResourceBundle.getBundle("net.archiloque.googlehistory.bundle");

    private static final DateFormat SIMPLE_DATE_FORMAT = new SimpleDateFormat("dd/MM/yyyy");

    public static void main(String[] args) throws Exception {
        System.out.println(Locale.getDefault().getLanguage());
        SIMPLE_DATE_FORMAT.setCalendar(new GregorianCalendar());
        new GoogleHistoryApp();
    }

    private GoogleHistoryApp() throws Exception {
        jFrame = new JFrame(APPLICATION_NAME);
        jFrame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        jFrame.setLayout(new BorderLayout());

        mainTextArea = new JTextArea(100, 120);
        JScrollPane mainScrollPane = new JScrollPane(mainTextArea, ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);
        mainTextArea.setEditable(false);

        csvWriter = new CSVWriter(new Writer() {
            @Override
            public void write(char[] cbuf, int off, int len) throws IOException {
                mainTextArea.append(new String(cbuf, off, len));
            }

            @Override
            public void flush() throws IOException {
            }

            @Override
            public void close() throws IOException {
            }
        }, ';');

        csvWriter.writeNext(resourceBundle.getString("COLUMNS_NAMES").split(","));

        logArea = new JTextArea(100, 120);
        logArea.setEditable(false);
        JScrollPane logAreaScrollPane = new JScrollPane(logArea, ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);
        logAreaScrollPane.setPreferredSize(new Dimension(-1, 100));

        JPanel topPanel = new JPanel();
        topPanel.setLayout(new FlowLayout());
        topPanel.add(new JLabel(resourceBundle.getString("SEARCH_NUMBER")));
        topPanel.add(searchNumber);
        topPanel.add(new JLabel(resourceBundle.getString("LAST_SEARCH")));
        topPanel.add(searchDate);
        jFrame.getContentPane().add(topPanel, BorderLayout.NORTH);
        jFrame.getContentPane().add(mainScrollPane, BorderLayout.CENTER);
        jFrame.getContentPane().add(logAreaScrollPane, BorderLayout.SOUTH);

        jFrame.pack();
        jFrame.setVisible(true);
        UserParamaters userParamaters = getUserCredentials();

        fetchHistory(userParamaters);
    }

    private void log(String logLine) {
        logArea.append(logLine + "\n");
    }

    private final Pattern DAY_PATTERN1 = Pattern.compile("(\\d+)\\s([^\\s]+)\\s(\\d{4})");
    private final Pattern DAY_PATTERN2 = Pattern.compile("(\\d+)\\s([^\\s]+)\\s(\\d{4}) \\(suite\\)");
    private final List<String> MONTHES = Arrays.asList("jan", "fév", "mar", "avr", "mai", "juin", "juil", "août", "sep", "oct", "nov", "déc");

    private Calendar getDateFromText(String dateString) {
        if (dateString.equals("Aujourd'hui")) {
            return Calendar.getInstance();
        } else if (dateString.equals("Aujourd'hui (suite)")) {
            return Calendar.getInstance();
        } else if (dateString.equals("Hier")) {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DATE, -1);
            return cal;
        } else if (dateString.equals("Hier (suite)")) {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DATE, -1);
            return cal;
        } else {
            Calendar cal = Calendar.getInstance();
            Matcher m = DAY_PATTERN1.matcher(dateString);
            if (m.matches()) {
                String month = m.group(2);
                int monthAsInt = MONTHES.indexOf(month);
                if (monthAsInt == -1) {
                    throw new RuntimeException(MessageFormat.format(resourceBundle.getString("UNKNOWN_MONTH"), month));
                }
                //noinspection MagicConstant
                cal.set(
                        Integer.parseInt(m.group(3)),
                        monthAsInt,
                        Integer.parseInt(m.group(1))
                );
                return cal;
            } else {
                m = DAY_PATTERN2.matcher(dateString);
                if (m.matches()) {
                    String month = m.group(2);
                    int monthAsInt = MONTHES.indexOf(month);
                    if (monthAsInt == -1) {
                        throw new RuntimeException(MessageFormat.format(resourceBundle.getString("UNKNOWN_MONTH"), month));
                    }
                    //noinspection MagicConstant
                    cal.set(
                            Integer.parseInt(m.group(3)),
                            monthAsInt,
                            Integer.parseInt(m.group(1))
                    );
                    return cal;
                }
                throw new RuntimeException(MessageFormat.format(resourceBundle.getString("CAN_T_PARSE_DATE"), dateString));
            }
        }
    }

    private static final String MAIN_HISTORY_PAGE = "https://history.google.com/history/?hl=fr";

    private void fetchHistory(UserParamaters userParamaters) throws Exception {
        List<GoogleHistoryEntry> result = new ArrayList<GoogleHistoryEntry>();

        WebClient webClient = new WebClient();
        webClient.getOptions().setJavaScriptEnabled(false);
        log(MessageFormat.format(resourceBundle.getString("OPENNING_PAGE"), MAIN_HISTORY_PAGE));
        HtmlPage loginPage = webClient.getPage(MAIN_HISTORY_PAGE);
        HtmlPage historyPage = reconnect(userParamaters, loginPage);
        GoogleHistoryEntry currentEntry = null;
        currentEntry = scrapePage(historyPage, userParamaters, currentEntry, result);
        while (currentEntry != null) {
            HtmlAnchor next;
            try {
                next = historyPage.getAnchorByText("Précédent");
                searchNumber.setText(Integer.toString(result.size()));
                searchDate.setText(SIMPLE_DATE_FORMAT.format(currentEntry.date.getTime()));
                log(resourceBundle.getString("NEXT_PAGE"));
                historyPage = next.click();
                Thread.sleep(3000);
            } catch (ElementNotFoundException e) {
                log(resourceBundle.getString("NEXT_PAGE_NOT_FOUND"));
                try {
                    historyPage.getElementByName("Passwd");
                } catch (ElementNotFoundException e2) {
                    log(resourceBundle.getString("CONNECTION_FORM_NOT_FOUND_STOPPING"));
                    JOptionPane.showMessageDialog(jFrame, resourceBundle.getString("HISTORY_FETCHED"));
                    return;
                }
                log(resourceBundle.getString("CONNECTION_FORM_FOUND"));
                historyPage = reconnect(userParamaters, historyPage);
            }
            currentEntry = scrapePage(historyPage, userParamaters, currentEntry, result);
        }
    }

    private HtmlPage reconnect(UserParamaters userParamaters, HtmlPage historyPage) throws IOException {
        HtmlForm loginForm;
        log(resourceBundle.getString("FILLING_CONNECTION_FORM"));
        loginForm = historyPage.getForms().get(0);
        try {
            loginForm.getInputByName("Email").setValueAttribute(userParamaters.login);
        } catch (ElementNotFoundException e) {
            // it a reconnexion
        }
        loginForm.getInputByName("Passwd").setValueAttribute(userParamaters.password);
        historyPage = loginForm.getInputByName("signIn").click();
        log(resourceBundle.getString("CONNECTION"));
        return historyPage;
    }

    private GoogleHistoryEntry scrapePage(HtmlPage historyPage, UserParamaters userParamaters, GoogleHistoryEntry currentEntry, List<GoogleHistoryEntry> result) throws IOException {
        HtmlForm pageForm;
        try {
            pageForm = historyPage.getFormByName("edit");
        } catch (ElementNotFoundException e) {
            if (currentEntry == null) {
                // failed to log the first time
                log(resourceBundle.getString("CAN_T_CONNECT"));
                JOptionPane.showMessageDialog(jFrame, resourceBundle.getString("CAN_T_CONNECT"), APPLICATION_NAME, JOptionPane.ERROR_MESSAGE);
                return null;
            }
            if (historyPage.getForms().isEmpty()) {
                return null;
            }
            historyPage = reconnect(userParamaters, historyPage);
            try {
                pageForm = historyPage.getFormByName("edit");
            } catch (ElementNotFoundException e2) {
                log(resourceBundle.getString("CAN_T_FIND_SEARCH_LIST"));
                return null;
            }
        }
        Calendar currentDate = null;
        for (DomNode domNode : pageForm.getChildren()) {
            if (domNode.querySelector("h1") != null) {
                currentDate = getDateFromText(domNode.querySelector("h1").getTextContent());
            } else if (domNode.querySelector(".result") != null) {
                List<DomNode> tds = domNode.querySelectorAll("td");
                String heure = tds.get(tds.size() - 1).getTextContent();

                DomNode firstLink = domNode.querySelector("a");
                String href = firstLink.getAttributes().getNamedItem("href").getNodeValue();
                if (href.startsWith("https://www.google.com/search?q=")) {
                    // it's a search
                    String terme = firstLink.getTextContent();
                    currentEntry = new GoogleHistoryEntry(terme, currentDate, heure);
                    csvWriter.writeNext(
                            new String[]{
                                    resourceBundle.getString("SEARCH_TYPE"),
                                    SIMPLE_DATE_FORMAT.format(currentEntry.date.getTime()),
                                    currentEntry.heure,
                                    currentEntry.terme});
                    result.add(currentEntry);
                } else {
                    // it's a result
                    if (userParamaters.recupereResultat) {
                        String terme = firstLink.getTextContent();

                        Node addresseNode = firstLink.getAttributes().getNamedItem("title");
                        String addresse;
                        if (addresseNode != null) {
                            addresse = addresseNode.getNodeValue();
                        } else {
                            addresse = "";
                        }
                        GoogleHistoryClick googleHistoryClick = new GoogleHistoryClick(terme, addresse, currentDate, heure);
                        csvWriter.writeNext(
                                new String[]{
                                        resourceBundle.getString("RESULT_TYPE"),
                                        SIMPLE_DATE_FORMAT.format(googleHistoryClick.date.getTime()),
                                        googleHistoryClick.heure,
                                        null,
                                        googleHistoryClick.titre,
                                        googleHistoryClick.adresse});
                    }
                }
            }
        }
        return currentEntry;
    }

    static final class GoogleHistoryEntry {

        private final String terme;

        private final String heure;

        private final Calendar date;

        GoogleHistoryEntry(String terme, Calendar date, String heure) {
            this.terme = terme;
            this.date = date;
            this.heure = heure;
        }
    }

    static final class GoogleHistoryClick {

        private final String titre;

        private final String adresse;

        private final Calendar date;

        private final String heure;

        GoogleHistoryClick(String titre, String adresse, Calendar date, String heure) {
            this.titre = titre;
            this.adresse = adresse;
            this.date = date;
            this.heure = heure;
        }
    }

    static final class UserParamaters {

        private final String login;

        private final String password;

        private final boolean recupereResultat;

        UserParamaters(String login, String password, boolean recupereResultat) {
            this.login = login;
            this.password = password;
            this.recupereResultat = recupereResultat;
        }
    }


    public UserParamaters getUserCredentials() {
        String login = JOptionPane.showInputDialog(
                jFrame,
                resourceBundle.getString("GOOGLE_EMAIL"),
                APPLICATION_NAME,
                JOptionPane.QUESTION_MESSAGE);
        if (login == null) {
            jFrame.dispose();
            return null;
        }
        String password = JOptionPane.showInputDialog(
                jFrame,
                resourceBundle.getString("GOOGLE_PASSWORD"),
                APPLICATION_NAME,
                JOptionPane.QUESTION_MESSAGE);
        if (password == null) {
            jFrame.dispose();
            return null;
        }
        boolean recupereResultat = JOptionPane.showOptionDialog(
                jFrame,
                resourceBundle.getString("SHOW_CLICKED_LINKS"),
                APPLICATION_NAME,
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                new Object[]{resourceBundle.getString("YES"), resourceBundle.getString("NO")},
                resourceBundle.getString("YES")
        ) == 0;
        return new UserParamaters(login, password, recupereResultat);

    }
}
