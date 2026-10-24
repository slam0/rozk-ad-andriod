package com.nazar0vsky.rozklad;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Języki aplikacji. W kodzie wszystkie napisy są po polsku; t() podmienia je na wybrany język.
 * Bez zależności od Androida (testowane na komputerze).
 */
public final class Lang {
    private Lang() {}

    /** Napisy dopisane w wersji na Androida: polski, angielski, rosyjski. */
    private static final String[][] EXTRA = {
        {"Wstecz", "Back", "Назад"},
        {"Wczytuję plik…", "Loading the file…", "Загружаю файл…"},
        {"Wybierz plik z telefonu", "Choose a file from the phone", "Выбрать файл из телефона"},
        {"Otwórz w przeglądarce stronę z rozkładem, wybierz przystanki i dotknij „Otwórz w aplikacji Rozkład”. Potem aplikacja zapamięta adres.",
         "Open the timetable website in your browser, choose stops and tap “Open in the Rozkład app”. The app will remember the address afterwards.",
         "Откройте в браузере сайт с расписанием, выберите остановки и нажмите «Открыть в приложении Rozkład». После этого приложение запомнит адрес."},
        {"Brak rozkładu.\n\nDotknij „Wczytaj” u góry i wybierz „Wybierz przystanki na stronie”. Po wybraniu przystanków dotknij tam „Otwórz w aplikacji Rozkład”.\n\nMożesz też wskazać plik rozklad.json zapisany w telefonie („Wybierz plik z telefonu”).%s",
         "No timetable.\n\nTap “Load” at the top and choose “Choose stops on the website”. After choosing stops, tap “Open in the Rozkład app” there.\n\nYou can also pick a rozklad.json file saved on the phone (“Choose a file from the phone”).%s",
         "Нет расписания.\n\nНажмите «Загрузить» вверху и выберите «Выбрать остановки на сайте». Выбрав остановки, нажмите там «Открыть в приложении Rozkład».\n\nМожно также указать файл rozklad.json, сохранённый в телефоне («Выбрать файл из телефона»).%s"},
        {"Telefon nie mógł nawiązać bezpiecznego połączenia (HTTPS). Sprawdź, czy data i godzina w telefonie są poprawne.",
         "The phone could not make a secure (HTTPS) connection. Check that the date and time on the phone are correct.",
         "Телефон не смог установить защищённое соединение (HTTPS). Проверьте, правильно ли установлены дата и время."},
        {"otwiera stronę w przeglądarce", "opens the website in the browser", "открывает сайт в браузере"},
        {"Wczytano plik", "File loaded", "Файл загружен"},
        {"Plik został wczytany i zapisany w aplikacji.", "The file was loaded and saved in the app.", "Файл загружен и сохранён в приложении."},
        {"Nie udało się wczytać pliku.", "Could not load the file.", "Не удалось загрузить файл."},
        {"Przypomnienia o odjazdach", "Departure reminders", "Напоминания об отправлении"},
        {"Powiadomienia o odjazdach autobusów, tramwajów, pociągów i metra", "Notifications about bus, tram, train and metro departures", "Уведомления об отправлении автобусов, трамваев, поездов и метро"},
        {"Aby przypomnienia działały, zezwól aplikacji na powiadomienia w ustawieniach telefonu.",
         "For reminders to work, allow notifications for the app in the phone settings.",
         "Чтобы напоминания работали, разрешите приложению уведомления в настройках телефона."},
        {"Telefon zadzwoni o %s, %d min przed odjazdem.", "The phone will alert you at %s, %d min before the departure.", "Телефон напомнит в %s, за %d мин до отправления."},
        {"Zamknij", "Close", "Закрыть"},
        {"Adres", "Address", "Адрес"},
        {"Wybierz", "Choose", "Выбрать"},
        {"Odśwież", "Refresh", "Обновить"},
        {"Wczytano ponownie", "Reloaded", "Загружено заново"},
        {"Rozkład wczytany z plików w aplikacji.", "The timetable was reloaded from the files in the app.", "Расписание загружено заново из файлов в приложении."},
        {"Plików w aplikacji: %d", "Files in the app: %d", "Файлов в приложении: %d"},
        {"Wszystkie rozkłady zapisane w aplikacji zostaną usunięte. Nowy pobierzesz ze strony.", "All timetables saved in the app will be deleted. You can download a new one from the website.", "Все сохранённые в приложении расписания будут удалены. Новое можно загрузить с сайта."},
    };

    private static int current = 0;
    private static final Map<String, String[]> INDEX = new HashMap<String, String[]>();
    private static boolean built = false;

    private static void build() {
        if (built) return;
        built = true;
        for (String[] r : LangTable.T) INDEX.put(r[0], r);
        for (String[] r : EXTRA) INDEX.put(r[0], r);
    }

    /** 0 polski, 1 angielski, 2 rosyjski */
    public static int current() { return current; }

    public static void setCurrent(int lang) { current = (lang == 1 || lang == 2) ? lang : 0; }

    public static String code() { return current == 1 ? "en" : (current == 2 ? "ru" : "pl"); }

    /** Język systemu telefonu jako 0/1/2 (nieznany = angielski). */
    public static int fromLocale(String languageTag) {
        String f = languageTag == null ? "en" : languageTag.toLowerCase(Locale.ROOT);
        if (f.startsWith("pl")) return 0;
        if (f.startsWith("ru")) return 2;
        return 1;
    }

    public static String t(String pl) {
        if (current == 0 || pl == null) return pl;
        build();
        String[] r = INDEX.get(pl);
        return r == null ? pl : r[current];
    }

    /** Tłumaczy szablon i podstawia argumenty (%s, %d, %02d). */
    public static String f(String pl, Object... args) {
        return String.format(Locale.US, t(pl), args);
    }

    /** Czy napis jest w tabeli (do testów kompletności). */
    public static boolean has(String pl) {
        build();
        return INDEX.containsKey(pl);
    }
}
