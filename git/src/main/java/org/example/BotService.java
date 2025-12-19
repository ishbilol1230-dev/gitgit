package org.example;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.ForwardMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class BotService extends TelegramLongPollingBot {

    private final String BOT_TOKEN = "8116422671:AAGUcCyOzq_qYrMeaR7TNpWSQ9738rfUC40";
    private final String BOT_USERNAME = "@LadistikaUzbot";
    private Connection connection;
    private final Map<Long, String> userStates = new HashMap<>();
    private final Map<Long, Advert> tempAdverts = new HashMap<>();
    private final Map<Long, DriverAdvert> tempDriverAdverts = new HashMap<>();
    private final Map<String, String> tempData = new HashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    // Asosiy guruh (@lagistika_botlar)
    private final String MAIN_GROUP_ID = "@lagistika_botlar";

    // Adminlar
    private final List<Long> ADMIN_IDS = Arrays.asList(7038296036L);

    // To'lov karta raqami
    private final String PAYMENT_CARD = "5614 6825 1378 8143\nKarta egasi: Jumaev.S";

    // Viloyatlar
    private final String[] REGIONS = {
            "Toshkent shahri", "Andijon viloyati", "Buxoro viloyati",
            "Jizzax viloyati", "Qashqadaryo viloyati", "Navoiy viloyati",
            "Namangan viloyati", "Samarqand viloyati", "Surxondaryo viloyati",
            "Sirdaryo viloyati", "Farg'ona viloyati", "Xorazm viloyati",
            "Qoraqalpog'iston Respublikasi"
    };

    // Kanallar/guruhlar ro'yxati
    private final List<TelegramGroup> groups = new ArrayList<>();

    // To'lov tizimini boshqarish
    private boolean paymentSystemEnabled = true;

    public BotService() {
        initDatabase();
        loadGroups();
        startAutoPosting();
        startCleanupScheduler();
        System.out.println("🤖 Bot yaratildi: " + BOT_USERNAME);
        System.out.println("💳 To'lov karta: " + PAYMENT_CARD);
        System.out.println("📊 Asosiy guruh: " + MAIN_GROUP_ID);
        System.out.println("📊 Qo'shimcha guruhlar: " + groups.size() + " ta");
        System.out.println("💰 To'lov tizimi holati: " + (paymentSystemEnabled ? "YOQILGAN" : "O'CHIRILGAN"));
    }

    // ==================== DATABASE METHODS ====================

    private void initDatabase() {
        try {
            Class.forName("org.h2.Driver");
            connection = DriverManager.getConnection("jdbc:h2:mem:yukbot;DB_CLOSE_DELAY=-1", "sa", "");

            // Avval barcha jadvallarni o'chirish
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("DROP TABLE IF EXISTS feedback");
                stmt.execute("DROP TABLE IF EXISTS telegram_groups");
                stmt.execute("DROP TABLE IF EXISTS payments");
                stmt.execute("DROP TABLE IF EXISTS drivers");
                stmt.execute("DROP TABLE IF EXISTS adverts");
                stmt.execute("DROP TABLE IF EXISTS users");
                System.out.println("🔄 Oldingi jadvallar o'chirildi!");
            }

            createTables();
            System.out.println("✅ Database yaratildi!");
        } catch (Exception e) {
            System.out.println("⚠️ Database xatosi: " + e.getMessage());
        }
    }

    private void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {

            // Users jadvali
            stmt.execute("CREATE TABLE IF NOT EXISTS users (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                    "telegram_id BIGINT UNIQUE," +
                    "username VARCHAR(100)," +
                    "first_name VARCHAR(100)," +
                    "phone VARCHAR(20)," +
                    "balance DECIMAL(10,2) DEFAULT 0," +
                    "is_admin BOOLEAN DEFAULT false," +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")");

            // Yuk beruvchilar e'lonlari (Yuk beraman)
            stmt.execute("CREATE TABLE IF NOT EXISTS adverts (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                    "user_id BIGINT," +
                    "from_region VARCHAR(100)," +
                    "from_address TEXT," +
                    "to_region VARCHAR(100)," +
                    "to_address TEXT," +
                    "product_type VARCHAR(100)," +
                    "weight VARCHAR(50)," +
                    "phone VARCHAR(20)," +
                    "price VARCHAR(50)," +
                    "additional_info TEXT," +
                    "type VARCHAR(20) DEFAULT 'yuk_berish'," +
                    "status VARCHAR(20) DEFAULT 'pending'," +
                    "is_paid BOOLEAN DEFAULT false," +
                    "payment_status VARCHAR(20) DEFAULT 'pending'," +
                    "post_count INTEGER DEFAULT 0," +
                    "order_number INTEGER DEFAULT 0," +
                    "expires_at TIMESTAMP," +
                    "last_posted TIMESTAMP," +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")");

            // Yuk oluvchilar e'lonlari (Yuk olaman - Haydovchilar)
            stmt.execute("CREATE TABLE IF NOT EXISTS drivers (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                    "user_id BIGINT," +
                    "region VARCHAR(100)," +
                    "phone VARCHAR(20)," +
                    "car_model VARCHAR(100)," +
                    "car_number VARCHAR(50)," +
                    "car_capacity VARCHAR(50)," +
                    "additional_info TEXT," +
                    "type VARCHAR(20) DEFAULT 'yuk_olish'," +
                    "status VARCHAR(20) DEFAULT 'pending'," +
                    "is_paid BOOLEAN DEFAULT false," +
                    "payment_status VARCHAR(20) DEFAULT 'pending'," +
                    "order_number INTEGER DEFAULT 0," +
                    "expires_at TIMESTAMP," +
                    "post_count INTEGER DEFAULT 0," +
                    "last_posted TIMESTAMP," +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")");

            // To'lovlar
            stmt.execute("CREATE TABLE IF NOT EXISTS payments (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                    "user_id BIGINT," +
                    "amount DECIMAL(10,2)," +
                    "type VARCHAR(20)," +
                    "advert_type VARCHAR(20)," +
                    "status VARCHAR(20) DEFAULT 'pending'," +
                    "admin_checked BOOLEAN DEFAULT false," +
                    "rejection_reason TEXT," +
                    "check_photo_id VARCHAR(200)," +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")");

            // Telegram guruhlari
            stmt.execute("CREATE TABLE IF NOT EXISTS telegram_groups (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                    "group_id VARCHAR(100) UNIQUE," +
                    "name VARCHAR(100)," +
                    "link TEXT," +
                    "is_active BOOLEAN DEFAULT true," +
                    "message_count INTEGER DEFAULT 0," +
                    "added_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")");

            // Taklif/shikoyatlar
            stmt.execute("CREATE TABLE IF NOT EXISTS feedback (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                    "user_id BIGINT," +
                    "message TEXT," +
                    "type VARCHAR(20)," +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ")");

            System.out.println("✅ 6 ta jadval yaratildi!");
        }
    }

    private void loadGroups() {
        try {
            // Asosiy guruhni qo'shamiz
            TelegramGroup mainGroup = new TelegramGroup();
            mainGroup.groupId = MAIN_GROUP_ID;
            mainGroup.name = "@Lagistika_Botlar";
            mainGroup.link = "https://t.me/lagistika_botlar";
            groups.add(mainGroup);

            // Bazadan qolgan guruhlarni yuklaymiz
            String sql = "SELECT * FROM telegram_groups WHERE is_active = true";
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {

                while (rs.next()) {
                    TelegramGroup group = new TelegramGroup();
                    group.groupId = rs.getString("group_id");
                    group.name = rs.getString("name");
                    group.link = rs.getString("link");
                    group.messageCount = rs.getInt("message_count");
                    groups.add(group);
                }
            }

            System.out.println("📢 Yuklangan guruhlar: " + groups.size() + " ta");
        } catch (SQLException e) {
            System.err.println("Guruhlarni yuklash xatosi: " + e.getMessage());
        }
    }

    private void startCleanupScheduler() {
        // Har kuni ertalab 5 kun oldingi e'lonlarni o'chiradi
        scheduler.scheduleAtFixedRate(() -> {
            try {
                cleanupOldAdverts();
            } catch (Exception e) {
                System.err.println("Cleanup xatosi: " + e.getMessage());
            }
        }, 0, 24, TimeUnit.HOURS);
    }

    private void cleanupOldAdverts() {
        try {
            String sql = "DELETE FROM adverts WHERE expires_at < CURRENT_TIMESTAMP";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                int deletedAdverts = pstmt.executeUpdate();
                if (deletedAdverts > 0) {
                    System.out.println("🧹 Yuk e'lonlari tozalandi: " + deletedAdverts);
                }
            }

            sql = "DELETE FROM drivers WHERE expires_at < CURRENT_TIMESTAMP";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                int deletedDrivers = pstmt.executeUpdate();
                if (deletedDrivers > 0) {
                    System.out.println("🧹 Haydovchi e'lonlari tozalandi: " + deletedDrivers);
                }
            }
        } catch (SQLException e) {
            System.err.println("Cleanup xatosi: " + e.getMessage());
        }
    }

    // ==================== TELEGRAM BOT METHODS ====================

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasMessage() && update.getMessage().hasText()) {
                handleMessage(update.getMessage());
            } else if (update.hasCallbackQuery()) {
                handleCallback(update.getCallbackQuery());
            } else if (update.hasMessage() && update.getMessage().hasPhoto()) {
                handlePhoto(update.getMessage());
            }
        } catch (Exception e) {
            System.err.println("❌ Xatolik: " + e.getMessage());
        }
    }

    private void handleMessage(Message msg) {
        Long chatId = msg.getChatId();
        String text = msg.getText();

        System.out.println("📩 " + chatId + ": " + text);

        saveUser(chatId, msg.getFrom().getFirstName(), msg.getFrom().getUserName());

        if (text.equals("/start")) {
            sendWelcomeMessage(chatId);
        } else if (text.equals("📦 E'lonlarni Ko'rish")) {
            showAdvertTypeSelection(chatId);
        } else if (text.equals("📢 E'lon berish")) {
            showAdvertCreationType(chatId);
        } else if (text.equals("🤖 Telegram Reklama")) {
            showTelegramPackages(chatId);
        } else if (text.equals("📋 Mening e'lonlarim")) {
            showMyAdverts(chatId);
        } else if (text.equals("✍️ Taklif/Shikoyat")) {
            askFeedback(chatId);
        } else if (text.equals("👨‍💼 Admin panel")) {
            if (ADMIN_IDS.contains(chatId)) {
                sendAdminPanel(chatId);
            } else {
                sendMessage(chatId, "❌ Siz admin emassiz!");
            }
        } else if (text.equals("🔙 Asosiy menyu")) {
            sendMainMenu(chatId);
        } else {
            handleUserState(chatId, text);
        }
    }

    private void sendWelcomeMessage(Long chatId) {
        String text = "🚚 *YukBot - Yuk Transport Platformasi*\n\n" +
                "Assalomu alaykum! Yuk yuklamoqchi yoki tashish xizmatini qidirmoqchimisiz?\n\n" +
                "✅ *Imkoniyatlar:*\n" +
                "• Yuklarni topish/sotish\n" +
                "• Haydovchilarni topish\n" +
                "• Guruhlarda avtomatik reklama\n" +
                "• Tez va ishonchli\n" +
                "• To'lov: " + PAYMENT_CARD;

        sendMainMenu(chatId);
    }

    private void sendMainMenu(Long chatId) {
        String text = "🏠 *Asosiy menyu*\n\nKerakli bo'limni tanlang:";

        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
        keyboard.setResizeKeyboard(true);
        keyboard.setOneTimeKeyboard(false);

        List<KeyboardRow> rows = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        row1.add("📦 E'lonlarni Ko'rish");
        row1.add("📢 E'lon berish");

        KeyboardRow row2 = new KeyboardRow();
        row2.add("🤖 Telegram Reklama");
        row2.add("📋 Mening e'lonlarim");

        KeyboardRow row3 = new KeyboardRow();
        row3.add("✍️ Taklif/Shikoyat");
        row3.add("👨‍💼 Admin panel");

        KeyboardRow row4 = new KeyboardRow();
        row4.add("🔙 Asosiy menyu");

        rows.add(row1);
        rows.add(row2);
        rows.add(row3);
        rows.add(row4);

        keyboard.setKeyboard(rows);

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        message.setParseMode("Markdown");
        message.setReplyMarkup(keyboard);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            System.err.println("Xabar yuborish xatosi: " + e.getMessage());
        }
    }

    // ==================== YANGI: E'LON TURINI TANLASH ====================

    private void showAdvertCreationType(Long chatId) {
        String text = "📝 *Qanday e'lon berasiz?*\n\nIltimos, birini tanlang:";

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton yukOlishBtn = new InlineKeyboardButton();
        yukOlishBtn.setText("🚚 Yuk olaman (Mashinam bor)");
        yukOlishBtn.setCallbackData("create_driver_ad");
        row1.add(yukOlishBtn);

        List<InlineKeyboardButton> row2 = new ArrayList<>();
        InlineKeyboardButton yukBerishBtn = new InlineKeyboardButton();
        yukBerishBtn.setText("📦 Yuk beraman (Yukim bor)");
        yukBerishBtn.setCallbackData("create_cargo_ad");
        row2.add(yukBerishBtn);

        rows.add(row1);
        rows.add(row2);
        markup.setKeyboard(rows);

        sendMessage(chatId, text, markup);
    }

    // ==================== YANGI: YUKLARNI KO'RISH TO'LOVI ====================

    private void showAdvertTypeSelection(Long chatId) {
        String text = "🔍 *Nimani ko'rmoqchisiz?*\n\n" +
                "E'lonlarni ko'rish uchun 15,000 so'm to'lashingiz kerak.\n\n" +
                "To'lov miqdori: *15,000 so'm*\n" +
                "Karta raqami: " + PAYMENT_CARD + "\n\n" +
                "To'lov qilgandan so'ng, chek rasmini yuboring.";

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton needCargoBtn = new InlineKeyboardButton();
        needCargoBtn.setText("📦 Sizga Yuk kerakmi?");
        needCargoBtn.setCallbackData("pay_view_cargo");
        row1.add(needCargoBtn);

        List<InlineKeyboardButton> row2 = new ArrayList<>();
        InlineKeyboardButton needDriverBtn = new InlineKeyboardButton();
        needDriverBtn.setText("🚚 Sizga Haydovchi kerakmi?");
        needDriverBtn.setCallbackData("pay_view_driver");
        row2.add(needDriverBtn);

        rows.add(row1);
        rows.add(row2);
        markup.setKeyboard(rows);

        sendMessage(chatId, text, markup);
    }

    private void askViewPayment(Long chatId, String advertType) {
        String text = String.format(
                "💳 *To'lov qilish:*\n\n" +
                        "E'lonlarni ko'rish uchun to'lov:\n" +
                        "Miqdor: *15,000 so'm*\n" +
                        "Karta raqami: *%s*\n\n" +
                        "To'lov qilgandan so'ng, chek rasmini shu yerga yuboring.\n" +
                        "Chekni yuborganingizdan so'ng, sizga e'lonlar ko'rsatiladi.",
                PAYMENT_CARD
        );

        sendMessage(chatId, text);

        userStates.put(chatId, "WAITING_VIEW_CHECK");
        tempData.put(chatId + "_view_type", advertType);
        tempData.put(chatId + "_view_amount", "15000");
    }

    // ==================== YANGI: YUK OLAMAN (HAYDOCHI) E'LONI ====================

    private void startDriverAdvert(Long chatId) {
        tempDriverAdverts.put(chatId, new DriverAdvert());
        userStates.put(chatId, "WAITING_DRIVER_REGION");
        sendRegionSelection(chatId, "📍 *Qaysi viloyatdasiz?*\nViloyat tanlang:", "driver");
    }

    private void sendRegionSelection(Long chatId, String question, String type) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (int i = 0; i < REGIONS.length; i += 2) {
            List<InlineKeyboardButton> row = new ArrayList<>();
            for (int j = 0; j < 2 && (i + j) < REGIONS.length; j++) {
                InlineKeyboardButton btn = new InlineKeyboardButton();
                btn.setText(REGIONS[i + j]);
                btn.setCallbackData("region_" + type + "_" + REGIONS[i + j].replace(" ", "_"));
                row.add(btn);
            }
            rows.add(row);
        }

        List<InlineKeyboardButton> backRow = new ArrayList<>();
        InlineKeyboardButton backBtn = new InlineKeyboardButton();
        backBtn.setText("🔙 Orqaga");
        backBtn.setCallbackData("back_to_main");
        backRow.add(backBtn);
        rows.add(backRow);

        markup.setKeyboard(rows);

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(question);
        message.setParseMode("Markdown");
        message.setReplyMarkup(markup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            System.err.println("Xabar yuborish xatosi: " + e.getMessage());
        }
    }

    // ==================== YUK BERAMAN (YUK) E'LONI ====================

    private void startCargoAdvert(Long chatId) {
        tempAdverts.put(chatId, new Advert());
        userStates.put(chatId, "WAITING_FROM_REGION");
        sendRegionSelection(chatId, "📍 *Yukni qayerdan olish kerak?*\nViloyat tanlang:", "from");
    }

    // ==================== CALLBACK HANDLER ====================

    private void handleCallback(CallbackQuery callback) {
        Long chatId = callback.getMessage().getChatId();
        String data = callback.getData();

        System.out.println("🔘 Callback: " + data + " from " + chatId);

        try {
            AnswerCallbackQuery answer = new AnswerCallbackQuery();
            answer.setCallbackQueryId(callback.getId());
            execute(answer);

            if (data.equals("create_driver_ad")) {
                startDriverAdvert(chatId);
            } else if (data.equals("create_cargo_ad")) {
                startCargoAdvert(chatId);
            } else if (data.equals("pay_view_cargo")) {
                askViewPayment(chatId, "cargo");
            } else if (data.equals("pay_view_driver")) {
                askViewPayment(chatId, "driver");
            } else if (data.startsWith("region_driver_")) {
                String region = data.replace("region_driver_", "").replace("_", " ");
                tempData.put(chatId + "_driver_region", region);
                userStates.put(chatId, "WAITING_DRIVER_PHONE");
                sendMessage(chatId, "📞 *Bog'lanish uchun telefon raqamingizni kiriting:*\nMisol: +998901234567");
            } else if (data.startsWith("region_from_")) {
                String region = data.replace("region_from_", "").replace("_", " ");
                tempData.put(chatId + "_from_region", region);
                sendMessage(chatId, "📍 *Yuklanadigan aniq manzilni kiriting:*\nMasalan: Chilonzor tumani, 5-kvartal");
                userStates.put(chatId, "WAITING_FROM_ADDRESS");
            } else if (data.startsWith("region_to_")) {
                String region = data.replace("region_to_", "").replace("_", " ");
                tempData.put(chatId + "_to_region", region);
                sendMessage(chatId, "📍 *Yuk olib boriladigan aniq manzilni kiriting:*\nMasalan: Shayxontohur tumani, 12-mavze");
                userStates.put(chatId, "WAITING_TO_ADDRESS");
            } else if (data.equals("back_to_main")) {
                sendMainMenu(chatId);
            } else if (data.startsWith("admin_")) {
                handleAdminCallback(chatId, data);
            } else if (data.startsWith("check_")) {
                handleCheckCallback(chatId, data);
            } else if (data.equals("confirm_driver_ad")) {
                showDriverAdConfirmation(chatId);
            } else if (data.equals("cancel_driver_ad")) {
                sendMessage(chatId, "❌ E'lon bekor qilindi.");
                sendMainMenu(chatId);
            } else if (data.equals("confirm_cargo_ad")) {
                showCargoAdConfirmation(chatId);
            } else if (data.equals("cancel_cargo_ad")) {
                sendMessage(chatId, "❌ E'lon bekor qilindi.");
                sendMainMenu(chatId);
            } else if (data.equals("pay_driver_ad")) {
                askPayment(chatId, 50000, "e'lon", "driver");
            } else if (data.equals("pay_cargo_ad")) {
                askPayment(chatId, 50000, "e'lon", "cargo");
            } else if (data.startsWith("tg_package_")) {
                String[] parts = data.split("_");
                String count = parts[2];
                int price = Integer.parseInt(parts[3]);
                askPayment(chatId, price, "telegram_reklama", count + "_marta");
            } else if (data.equals("confirm_free_driver_ad")) {
                activateDriverAdvertForFree(chatId);
            } else if (data.equals("confirm_free_cargo_ad")) {
                activateCargoAdvertForFree(chatId);
            } else if (data.startsWith("tg_free_package_")) {
                String count = data.replace("tg_free_package_", "");
                activateTelegramPackageForFree(chatId, count);
            } else if (data.equals("admin_back")) {
                sendAdminPanel(chatId);
            } else if (data.equals("btn_advertise")) {
                // Yangi: "Reklama berish" tugmasi bosilganda
                handleAdvertiseButton(callback.getMessage().getChatId());
            }

        } catch (Exception e) {
            System.err.println("Callback xatosi: " + e.getMessage());
        }
    }

    // ==================== YANGI: "REKLAMA BERISH" TUGMASI ====================

    private void handleAdvertiseButton(Long chatId) {
        try {
            // Botga /start xabarini yuborish
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText("/start");
            execute(message);

            // Biroz kutib, asosiy menyuni yuborish
            Thread.sleep(500);
            sendWelcomeMessage(chatId);

        } catch (Exception e) {
            System.err.println("Reklama berish tugmasi xatosi: " + e.getMessage());
        }
    }

    // ==================== YANGI: TO'LOV SISTEMASI ====================

    private void askPayment(Long chatId, int amount, String type, String advertType) {
        String text = String.format(
                "💳 *To'lov qilish:*\n\n" +
                        "Miqdor: *%,d so'm*\n" +
                        "Karta raqami: *%s*\n\n" +
                        "To'lov qilgandan so'ng, chek rasmini shu yerga yuboring.\n" +
                        "💡 *Eslatma:* Chekda to'lov summasi aniq ko'rinishi kerak!\n" +
                        "Chekni admin tekshirgach, e'loningiz faollashtiriladi.",
                amount, PAYMENT_CARD
        );

        sendMessage(chatId, text);

        userStates.put(chatId, "WAITING_CHECK");
        tempData.put(chatId + "_payment_amount", String.valueOf(amount));
        tempData.put(chatId + "_payment_type", type);
        tempData.put(chatId + "_advert_type", advertType);
    }

    // ==================== USER STATE HANDLER ====================

    private void handleUserState(Long chatId, String text) {
        String state = userStates.get(chatId);
        if (state == null) return;

        switch (state) {
            case "WAITING_VIEW_CHECK":
                // Bu holat faqat foto uchun ishlaydi
                break;

            case "WAITING_DRIVER_PHONE":
                DriverAdvert driverAdvert = tempDriverAdverts.get(chatId);
                driverAdvert.setPhone(text);
                userStates.put(chatId, "WAITING_CAR_MODEL");
                sendMessage(chatId, "🚗 *Mashina markasi va modelini kiriting:*\nMasalan: Kamaz, Fura Isuzu NPR");
                break;

            case "WAITING_CAR_MODEL":
                driverAdvert = tempDriverAdverts.get(chatId);
                driverAdvert.setCarModel(text);
                userStates.put(chatId, "WAITING_CAR_NUMBER");
                sendMessage(chatId, "🔢 *Mashina raqamini kiriting:*\nMasalan: 01 A 123 AA");
                break;

            case "WAITING_CAR_NUMBER":
                driverAdvert = tempDriverAdverts.get(chatId);
                driverAdvert.setCarNumber(text);
                userStates.put(chatId, "WAITING_CAR_CAPACITY");
                sendMessage(chatId, "⚖️ *Mashinangizning yuk ko'tarish sig'imi:*\nMasalan: 1 tonna, 3 tonna, 5 tonna");
                break;

            case "WAITING_CAR_CAPACITY":
                driverAdvert = tempDriverAdverts.get(chatId);
                driverAdvert.setCarCapacity(text);
                userStates.put(chatId, "WAITING_DRIVER_ADDITIONAL");
                sendMessage(chatId, "ℹ️ *Qo'shimcha ma'lumotlar:*\nIsh vaqti, xizmat ko'rsatish hududi, narx va boshqalar");
                break;

            case "WAITING_DRIVER_ADDITIONAL":
                driverAdvert = tempDriverAdverts.get(chatId);
                driverAdvert.setAdditionalInfo(text);
                driverAdvert.setRegion(tempData.get(chatId + "_driver_region"));
                showDriverAdPreview(chatId);
                break;

            case "WAITING_FROM_ADDRESS":
                Advert advert = tempAdverts.get(chatId);
                advert.setFromAddress(text);
                advert.setFromRegion(tempData.get(chatId + "_from_region"));
                userStates.put(chatId, "WAITING_TO_REGION");
                sendRegionSelection(chatId, "📍 *Yuk qayerga olib boriladi?*\nViloyat tanlang:", "to");
                break;

            case "WAITING_TO_ADDRESS":
                advert = tempAdverts.get(chatId);
                advert.setToAddress(text);
                advert.setToRegion(tempData.get(chatId + "_to_region"));
                userStates.put(chatId, "WAITING_PRODUCT_TYPE");
                sendMessage(chatId, "📦 *Qanday turdagi mahsulot yuklanadi?*\nMasalan: Mebellar, Oziq-ovqat, Qurilish materiallari");
                break;

            case "WAITING_PRODUCT_TYPE":
                advert = tempAdverts.get(chatId);
                advert.setProductType(text);
                userStates.put(chatId, "WAITING_WEIGHT");
                sendMessage(chatId, "⚖️ *Yuk og'irligini kiriting:*\nMasalan: 500 kg, 1 tonna, 2.5 tonna");
                break;

            case "WAITING_WEIGHT":
                advert = tempAdverts.get(chatId);
                advert.setWeight(text);
                userStates.put(chatId, "WAITING_CARGO_PHONE");
                sendMessage(chatId, "📞 *Bog'lanish uchun telefon raqam:*\nMisol: +998901234567");
                break;

            case "WAITING_CARGO_PHONE":
                advert = tempAdverts.get(chatId);
                advert.setPhone(text);
                userStates.put(chatId, "WAITING_PRICE");
                sendMessage(chatId, "💵 *Yuk mashinasi uchun qancha xizmat narxi?*\nMasalan: 1,500,000 so'm, 2 million");
                break;

            case "WAITING_PRICE":
                advert = tempAdverts.get(chatId);
                advert.setPrice(text);
                userStates.put(chatId, "WAITING_ADDITIONAL");
                sendMessage(chatId, "ℹ️ *Qo'shimcha ma'lumotlar:*\nmashina turi, vaqti va boshqa zarur ma'lumotlar");
                break;

            case "WAITING_ADDITIONAL":
                advert = tempAdverts.get(chatId);
                advert.setAdditionalInfo(text);
                showCargoAdPreview(chatId);
                break;

            case "WAITING_FEEDBACK":
                saveFeedback(chatId, text, "feedback");
                sendMessage(chatId, "✅ Fikringiz qabul qilindi! Rahmat!");
                sendMainMenu(chatId);
                userStates.remove(chatId);
                break;

            case "WAITING_REJECTION_REASON":
                handleRejectionReason(chatId, text);
                break;

            case "WAITING_GROUP_LINK":
                addGroup(chatId, text);
                break;

            case "WAITING_BROADCAST":
                sendBroadcastToAllUsers(chatId, text);
                break;

            case "WAITING_AD_TEXT":
                tempData.put(chatId + "_ad_text", text);
                sendMessage(chatId, "✏️ *Reklama tugmasi uchun matn kiriting:*\n" +
                        "Masalan: 'Botga o'tish', 'Xarid qilish', 'Ro'yxatdan o'tish'");
                userStates.put(chatId, "WAITING_AD_BUTTON");
                break;

            case "WAITING_AD_BUTTON":
                String adText = tempData.get(chatId + "_ad_text");
                createUserAdvertisement(chatId, adText, text);
                userStates.remove(chatId);
                tempData.remove(chatId + "_ad_text");
                break;
        }
    }

    // ==================== YANGI: FOYDALANUVCHILAR UCHUN REKLAMA ====================

    private void startUserAdvertisement(Long adminId) {
        sendMessage(adminId, "📢 *Foydalanuvchilar uchun reklama*\n\n" +
                "Reklama uchun matn kiriting. Bu reklama:\n" +
                "1. Barcha foydalanuvchilarga 1 marta yuboriladi\n" +
                "2. Barcha guruhlarga 30 marta (har 1 daqiqada 1 marta) yuboriladi\n" +
                "3. Reklama ostida botga o'tish tugmasi bo'ladi");

        userStates.put(adminId, "WAITING_AD_TEXT");
        tempData.put(adminId + "_ad_type", "user_ad");
    }

    private void createUserAdvertisement(Long adminId, String adText, String buttonText) {
        // 1. Reklamani barcha foydalanuvchilarga bir marta yuborish
        sendAdToAllUsers(adminId, adText, buttonText);

        // 2. Reklamani guruhlarga 30 marta yuborish
        startGroupAdPosting(adminId, adText, buttonText, 30);

        // 3. Adminga xabar berish
        sendMessage(adminId, "✅ Reklama muvaffaqiyatli yaratildi!\n\n" +
                "📊 Reklama statistikasi:\n" +
                "• Barcha foydalanuvchilarga: 1 marta\n" +
                "• Barcha guruhlarga: 30 marta (har daqiqada)\n" +
                "• Tugma matni: " + buttonText);
    }

    private void sendAdToAllUsers(Long adminId, String adText, String buttonText) {
        try {
            String sql = "SELECT telegram_id FROM users";
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {

                int sent = 0;
                int failed = 0;

                while (rs.next()) {
                    Long userId = rs.getLong("telegram_id");
                    try {
                        String message = "📢 *Reklama:*\n\n" + adText;

                        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
                        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
                        List<InlineKeyboardButton> row = new ArrayList<>();

                        InlineKeyboardButton btn = new InlineKeyboardButton();
                        btn.setText(buttonText);
                        btn.setUrl("https://t.me/" + BOT_USERNAME.replace("@", ""));
                        row.add(btn);

                        rows.add(row);
                        markup.setKeyboard(rows);

                        SendMessage msg = new SendMessage();
                        msg.setChatId(userId.toString());
                        msg.setText(message);
                        msg.setParseMode("Markdown");
                        msg.setReplyMarkup(markup);

                        execute(msg);
                        sent++;
                        Thread.sleep(50); // Rate limit uchun

                    } catch (Exception e) {
                        failed++;
                    }
                }

                sendMessage(adminId, String.format(
                        "✅ Foydalanuvchilarga reklama yuborildi!\n\n" +
                                "✅ Yuborildi: %d foydalanuvchi\n" +
                                "❌ Yuborilmadi: %d foydalanuvchi",
                        sent, failed
                ));

            }
        } catch (SQLException e) {
            System.err.println("Foydalanuvchilarga reklama yuborish xatosi: " + e.getMessage());
            sendMessage(adminId, "❌ Xatolik: " + e.getMessage());
        }
    }

    private void startGroupAdPosting(Long adminId, String adText, String buttonText, int count) {
        new Thread(() -> {
            try {
                sendMessage(adminId, "🔄 Guruhlarga reklama tashlash boshlandi... (Jami: " + count + " marta)");

                for (int i = 1; i <= count; i++) {
                    for (TelegramGroup group : groups) {
                        try {
                            String message = "📢 *Reklama:*\n\n" + adText +
                                    "\n\n━━━━━━━━━━━━━━━\n" +
                                    "#Reklama #YukBot";

                            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
                            List<List<InlineKeyboardButton>> rows = new ArrayList<>();
                            List<InlineKeyboardButton> row = new ArrayList<>();

                            InlineKeyboardButton btn = new InlineKeyboardButton();
                            btn.setText(buttonText);
                            btn.setUrl("https://t.me/" + BOT_USERNAME.replace("@", ""));
                            row.add(btn);

                            rows.add(row);
                            markup.setKeyboard(rows);

                            SendMessage msg = new SendMessage();
                            msg.setChatId(group.groupId);
                            msg.setText(message);
                            msg.setParseMode("Markdown");
                            msg.setReplyMarkup(markup);

                            execute(msg);

                            System.out.println("📢 Reklama yuborildi: " + group.groupId + " (" + i + "/" + count + ")");

                            Thread.sleep(1000); // 1 soniya kutish guruhlar orasida

                        } catch (Exception e) {
                            System.err.println("Guruhga reklama yuborish xatosi: " + group.groupId + " - " + e.getMessage());
                        }
                    }

                    // Har 1 daqiqada (60 soniya) keyin keyingi post
                    if (i < count) {
                        Thread.sleep(60000); // 1 daqiqa = 60000 millisekund
                    }
                }

                sendMessage(adminId, "✅ Guruhlarga reklama tashlash yakunlandi!\n" +
                        "📊 Jami: " + count + " marta tashlandi");

            } catch (InterruptedException e) {
                System.err.println("Guruh reklama tashlash xatosi: " + e.getMessage());
            }
        }).start();
    }

    // ==================== YANGI: DRIVER ADVERT PREVIEW ====================

    private void showDriverAdPreview(Long chatId) {
        DriverAdvert advert = tempDriverAdverts.get(chatId);

        String preview = String.format(
                "📋 *Haydovchi e'loni:*\n\n" +
                        "📍 *Viloyat:* %s\n" +
                        "📞 *Telefon:* %s\n" +
                        "🚗 *Mashina:* %s\n" +
                        "🔢 *Raqami:* %s\n" +
                        "⚖️ *Sig'im:* %s\n" +
                        "ℹ️ *Qo'shimcha:* %s\n\n" +
                        "*Ma'lumotlar to'g'rimi?*",
                advert.getRegion(),
                advert.getPhone(),
                advert.getCarModel(),
                advert.getCarNumber(),
                advert.getCarCapacity(),
                advert.getAdditionalInfo()
        );

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        List<InlineKeyboardButton> row = new ArrayList<>();
        InlineKeyboardButton confirmBtn = new InlineKeyboardButton();
        confirmBtn.setText("✅ Tasdiqlash");
        confirmBtn.setCallbackData("confirm_driver_ad");
        row.add(confirmBtn);

        InlineKeyboardButton cancelBtn = new InlineKeyboardButton();
        cancelBtn.setText("❌ Rad etish");
        cancelBtn.setCallbackData("cancel_driver_ad");
        row.add(cancelBtn);

        rows.add(row);
        markup.setKeyboard(rows);

        sendMessage(chatId, preview, markup);
    }

    private void showCargoAdPreview(Long chatId) {
        Advert advert = tempAdverts.get(chatId);

        String preview = String.format(
                "📋 *Yuk e'loni:*\n\n" +
                        "📍 *Qayerdan:* %s\n   %s\n" +
                        "📍 *Qayerga:* %s\n   %s\n" +
                        "📦 *Yuk turi:* %s\n" +
                        "⚖️ *Og'irlik:* %s\n" +
                        "📞 *Telefon:* %s\n" +
                        "💰 *Narx:* %s\n" +
                        "ℹ️ *Qo'shimcha:* %s\n\n" +
                        "*Ma'lumotlar to'g'rimi?*",
                advert.getFromRegion(),
                advert.getFromAddress(),
                advert.getToRegion(),
                advert.getToAddress(),
                advert.getProductType(),
                advert.getWeight(),
                advert.getPhone(),
                advert.getPrice(),
                advert.getAdditionalInfo()
        );

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        List<InlineKeyboardButton> row = new ArrayList<>();
        InlineKeyboardButton confirmBtn = new InlineKeyboardButton();
        confirmBtn.setText("✅ Tasdiqlash");
        confirmBtn.setCallbackData("confirm_cargo_ad");
        row.add(confirmBtn);

        InlineKeyboardButton cancelBtn = new InlineKeyboardButton();
        cancelBtn.setText("❌ Rad etish");
        cancelBtn.setCallbackData("cancel_cargo_ad");
        row.add(cancelBtn);

        rows.add(row);
        markup.setKeyboard(rows);

        sendMessage(chatId, preview, markup);
    }

    private void showDriverAdConfirmation(Long chatId) {
        if (paymentSystemEnabled) {
            // Normal to'lov tizimi
            String text = "✅ *Haydovchi e'loni tasdiqlandi!*\n\n" +
                    "Endi e'loningizni faollashtirish uchun to'lov qilishingiz kerak.\n" +
                    "To'lov miqdori: *50,000 so'm*\n\n" +
                    "To'lov qilgandan so'ng, chek rasmini yuboring.\n\n" +
                    "💳 Karta raqami: " + PAYMENT_CARD;

            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();

            List<InlineKeyboardButton> row = new ArrayList<>();
            InlineKeyboardButton payBtn = new InlineKeyboardButton();
            payBtn.setText("💳 50,000 so'm to'lash");
            payBtn.setCallbackData("pay_driver_ad");
            row.add(payBtn);

            rows.add(row);
            markup.setKeyboard(rows);

            sendMessage(chatId, text, markup);
        } else {
            // To'lov tizimi o'chirilgan - TEKIN
            String text = "✅ *Haydovchi e'loni tasdiqlandi!*\n\n" +
                    "To'lov tizimi vaqtincha o'chirilgan.\n" +
                    "E'loningiz TEKIN faollashtiriladi!\n\n" +
                    "📝 *Diqqat:* Bu vaqtinchalik imkoniyat. Keyinchalik to'lov talab qilinishi mumkin.";

            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();

            List<InlineKeyboardButton> row = new ArrayList<>();
            InlineKeyboardButton confirmBtn = new InlineKeyboardButton();
            confirmBtn.setText("✅ TEKIN faollashtirish");
            confirmBtn.setCallbackData("confirm_free_driver_ad");
            row.add(confirmBtn);

            InlineKeyboardButton cancelBtn = new InlineKeyboardButton();
            cancelBtn.setText("❌ Bekor qilish");
            cancelBtn.setCallbackData("cancel_driver_ad");
            row.add(cancelBtn);

            rows.add(row);
            markup.setKeyboard(rows);

            sendMessage(chatId, text, markup);
        }
    }

    private void showCargoAdConfirmation(Long chatId) {
        if (paymentSystemEnabled) {
            // Normal to'lov tizimi
            String text = "✅ *Yuk e'loni tasdiqlandi!*\n\n" +
                    "Endi e'loningizni faollashtirish uchun to'lov qilishingiz kerak.\n" +
                    "To'lov miqdori: *50,000 so'm*\n\n" +
                    "To'lov qilgandan so'ng, chek rasmini yuboring.\n\n" +
                    "💳 Karta raqami: " + PAYMENT_CARD;

            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();

            List<InlineKeyboardButton> row = new ArrayList<>();
            InlineKeyboardButton payBtn = new InlineKeyboardButton();
            payBtn.setText("💳 50,000 so'm to'lash");
            payBtn.setCallbackData("pay_cargo_ad");
            row.add(payBtn);

            rows.add(row);
            markup.setKeyboard(rows);

            sendMessage(chatId, text, markup);
        } else {
            // To'lov tizimi o'chirilgan - TEKIN
            String text = "✅ *Yuk e'loni tasdiqlandi!*\n\n" +
                    "To'lov tizimi vaqtincha o'chirilgan.\n" +
                    "E'loningiz TEKIN faollashtiriladi!\n\n" +
                    "📝 *Diqqat:* Bu vaqtinchalik imkoniyat. Keyinchalik to'lov talab qilinishi mumkin.";

            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();

            List<InlineKeyboardButton> row = new ArrayList<>();
            InlineKeyboardButton confirmBtn = new InlineKeyboardButton();
            confirmBtn.setText("✅ TEKIN faollashtirish");
            confirmBtn.setCallbackData("confirm_free_cargo_ad");
            row.add(confirmBtn);

            InlineKeyboardButton cancelBtn = new InlineKeyboardButton();
            cancelBtn.setText("❌ Bekor qilish");
            cancelBtn.setCallbackData("cancel_cargo_ad");
            row.add(cancelBtn);

            rows.add(row);
            markup.setKeyboard(rows);

            sendMessage(chatId, text, markup);
        }
    }

    // ==================== PHOTO HANDLER (CHEKLAR) ====================

    private void handlePhoto(Message msg) {
        Long chatId = msg.getChatId();
        String state = userStates.get(chatId);

        if (state == null) return;

        if ("WAITING_CHECK".equals(state)) {
            try {
                // To'lov ma'lumotlari
                int amount = Integer.parseInt(tempData.get(chatId + "_payment_amount"));
                String type = tempData.get(chatId + "_payment_type");
                String advertType = tempData.get(chatId + "_advert_type");

                // E'lonni saqlash
                if ("e'lon".equals(type)) {
                    if ("driver".equals(advertType)) {
                        DriverAdvert advert = tempDriverAdverts.get(chatId);
                        if (advert != null) {
                            saveDriverAdvert(chatId, advert);
                        }
                    } else if ("cargo".equals(advertType)) {
                        Advert advert = tempAdverts.get(chatId);
                        if (advert != null) {
                            saveCargoAdvert(chatId, advert);
                        }
                    }
                }

                // To'lovni saqlash
                String photoId = msg.getPhoto().get(msg.getPhoto().size() - 1).getFileId();
                int paymentId = savePayment(chatId, amount, type, advertType, photoId);

                // Foydalanuvchiga xabar
                sendMessage(chatId, "✅ Chek qabul qilindi! Admin tekshirgach, e'loningiz faollashtiriladi.");

                // Adminlarga yuborish
                sendCheckToAdmins(chatId, msg, paymentId, amount, type, advertType);

                // Tozalash
                userStates.remove(chatId);
                tempData.remove(chatId + "_payment_amount");
                tempData.remove(chatId + "_payment_type");
                tempData.remove(chatId + "_advert_type");
                tempAdverts.remove(chatId);
                tempDriverAdverts.remove(chatId);

            } catch (Exception e) {
                System.err.println("Chek qabul qilish xatosi: " + e.getMessage());
                sendMessage(chatId, "❌ Xatolik yuz berdi. Iltimos, qayta urinib ko'ring.");
            }
        } else if ("WAITING_VIEW_CHECK".equals(state)) {
            try {
                String viewType = tempData.get(chatId + "_view_type");
                int amount = Integer.parseInt(tempData.get(chatId + "_view_amount"));
                String photoId = msg.getPhoto().get(msg.getPhoto().size() - 1).getFileId();

                // To'lovni saqlash
                int paymentId = savePayment(chatId, amount, "view_ads", viewType, photoId);

                // Avtomatik tasdiqlash (admin tekshirishi shart emas)
                String updateSql = "UPDATE payments SET status = 'confirmed', admin_checked = true WHERE id = ?";
                try (PreparedStatement updateStmt = connection.prepareStatement(updateSql)) {
                    updateStmt.setInt(1, paymentId);
                    updateStmt.executeUpdate();
                }

                sendMessage(chatId, "✅ To'lovingiz qabul qilindi! Endi e'lonlarni ko'rishingiz mumkin.");

                // E'lonlarni ko'rsatish (har biri alohida)
                if ("cargo".equals(viewType)) {
                    showCargoAdvertsIndividually(chatId);
                } else if ("driver".equals(viewType)) {
                    showDriverAdvertsIndividually(chatId);
                }

                // Tozalash
                userStates.remove(chatId);
                tempData.remove(chatId + "_view_type");
                tempData.remove(chatId + "_view_amount");

            } catch (Exception e) {
                System.err.println("Ko'rish uchun to'lov xatosi: " + e.getMessage());
                sendMessage(chatId, "❌ Xatolik yuz berdi. Iltimos, qayta urinib ko'ring.");
            }
        }
    }

    // ==================== YANGI: HAR BIR E'LONNI ALOHIDA KO'RSATISH ====================

    private void showCargoAdvertsIndividually(Long chatId) {
        try {
            String sql = "SELECT a.*, u.first_name FROM adverts a " +
                    "JOIN users u ON a.user_id = u.id " +
                    "WHERE a.status = 'active' AND a.is_paid = true " +
                    "AND a.expires_at > CURRENT_TIMESTAMP " +
                    "ORDER BY a.order_number DESC LIMIT 20";

            try (PreparedStatement pstmt = connection.prepareStatement(sql);
                 ResultSet rs = pstmt.executeQuery()) {

                int count = 0;
                while (rs.next()) {
                    count++;

                    // Har bir e'lon uchun alohida xabar tayyorlash
                    String messageText = formatCargoAdvertForView(rs);

                    // Har bir e'lon uchun alohida tugmalar
                    InlineKeyboardMarkup markup = createCargoAdvertButtons(rs);

                    // Xabarni yuborish
                    SendMessage message = new SendMessage();
                    message.setChatId(chatId.toString());
                    message.setText(messageText);
                    message.setParseMode("HTML");
                    message.setReplyMarkup(markup);

                    execute(message);

                    // Har bir xabar orasida 1 soniya kutish
                    Thread.sleep(1000);
                }

                if (count == 0) {
                    sendMessage(chatId, "📭 Hozircha mavjud yuklar yo'q");
                } else {
                    sendMessage(chatId, "✅ " + count + " ta yuk e'loni ko'rsatildi");
                }

            }

        } catch (Exception e) {
            System.err.println("Yuklarni ko'rsatish xatosi: " + e.getMessage());
        }
    }

    private void showDriverAdvertsIndividually(Long chatId) {
        try {
            String sql = "SELECT d.*, u.first_name FROM drivers d " +
                    "JOIN users u ON d.user_id = u.id " +
                    "WHERE d.status = 'active' AND d.is_paid = true " +
                    "AND d.expires_at > CURRENT_TIMESTAMP " +
                    "ORDER BY d.order_number DESC LIMIT 20";

            try (PreparedStatement pstmt = connection.prepareStatement(sql);
                 ResultSet rs = pstmt.executeQuery()) {

                int count = 0;
                while (rs.next()) {
                    count++;

                    // Har bir e'lon uchun alohida xabar tayyorlash
                    String messageText = formatDriverAdvertForView(rs);

                    // Har bir e'lon uchun alohida tugmalar
                    InlineKeyboardMarkup markup = createDriverAdvertButtons(rs);

                    // Xabarni yuborish
                    SendMessage message = new SendMessage();
                    message.setChatId(chatId.toString());
                    message.setText(messageText);
                    message.setParseMode("HTML");
                    message.setReplyMarkup(markup);

                    execute(message);

                    // Har bir xabar orasida 1 soniya kutish
                    Thread.sleep(1000);
                }

                if (count == 0) {
                    sendMessage(chatId, "📭 Hozircha mavjud haydovchilar yo'q");
                } else {
                    sendMessage(chatId, "✅ " + count + " ta haydovchi e'loni ko'rsatildi");
                }

            }

        } catch (Exception e) {
            System.err.println("Haydovchilarni ko'rsatish xatosi: " + e.getMessage());
        }
    }

    private String formatCargoAdvertForView(ResultSet rs) throws SQLException {
        return String.format(
                "<b>🚚 Yuk topildi #%d</b>\n\n" +
                        "<b>📍 Qayerdan:</b> %s\n" +
                        "<i>%s</i>\n" +
                        "<b>📍 Qayerga:</b> %s\n" +
                        "<i>%s</i>\n" +
                        "<b>📦 Yuk turi:</b> %s\n" +
                        "<b>⚖️ Og'irligi:</b> %s\n" +
                        "<b>💰 Narxi:</b> %s\n" +
                        "<b>📞 Telefon:</b> %s\n" +
                        "<b>ℹ️ Qo'shimcha:</b> %s\n\n" +
                        "<i>E'lon beruvchi: %s</i>\n" +
                        "━━━━━━━━━━━━━━━━\n" +
                        "<b>📢 O'zingizning e'loningizni joylashtirish uchun:</b>",
                rs.getInt("order_number"),
                rs.getString("from_region") != null ? rs.getString("from_region") : "",
                rs.getString("from_address") != null ? rs.getString("from_address") : "",
                rs.getString("to_region") != null ? rs.getString("to_region") : "",
                rs.getString("to_address") != null ? rs.getString("to_address") : "",
                rs.getString("product_type") != null ? rs.getString("product_type") : "",
                rs.getString("weight") != null ? rs.getString("weight") : "",
                rs.getString("price") != null ? rs.getString("price") : "",
                rs.getString("phone") != null ? rs.getString("phone") : "",
                rs.getString("additional_info") != null ? rs.getString("additional_info") : "",
                rs.getString("first_name") != null ? rs.getString("first_name") : "Noma'lum"
        );
    }

    private String formatDriverAdvertForView(ResultSet rs) throws SQLException {
        return String.format(
                "<b>🚚 Haydovchi topildi #%d</b>\n\n" +
                        "<b>📍 Viloyat:</b> %s\n" +
                        "<b>📞 Telefon:</b> %s\n" +
                        "<b>🚗 Mashina:</b> %s\n" +
                        "<b>🔢 Raqami:</b> %s\n" +
                        "<b>⚖️ Sig'imi:</b> %s\n" +
                        "<b>ℹ️ Qo'shimcha:</b> %s\n\n" +
                        "<i>E'lon beruvchi: %s</i>\n" +
                        "━━━━━━━━━━━━━━━━\n" +
                        "<b>📢 O'zingizning e'loningizni joylashtirish uchun:</b>",
                rs.getInt("order_number"),
                rs.getString("region") != null ? rs.getString("region") : "",
                rs.getString("phone") != null ? rs.getString("phone") : "",
                rs.getString("car_model") != null ? rs.getString("car_model") : "",
                rs.getString("car_number") != null ? rs.getString("car_number") : "",
                rs.getString("car_capacity") != null ? rs.getString("car_capacity") : "",
                rs.getString("additional_info") != null ? rs.getString("additional_info") : "",
                rs.getString("first_name") != null ? rs.getString("first_name") : "Noma'lum"
        );
    }

    private InlineKeyboardMarkup createCargoAdvertButtons(ResultSet rs) throws SQLException {
        String phone = rs.getString("phone");

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        if (phone != null && !phone.isEmpty()) {
            List<InlineKeyboardButton> row1 = new ArrayList<>();
            InlineKeyboardButton contactBtn = new InlineKeyboardButton();
            contactBtn.setText("📞 Bog'lanish");
            String cleanPhone = phone.replace("+", "").replaceAll("\\s", "");
            contactBtn.setUrl("https://t.me/" + cleanPhone);
            row1.add(contactBtn);
            rows.add(row1);
        }

        List<InlineKeyboardButton> row2 = new ArrayList<>();
        InlineKeyboardButton advertiseBtn = new InlineKeyboardButton();
        advertiseBtn.setText("📢 E'lon berish");
        advertiseBtn.setUrl("https://t.me/" + BOT_USERNAME.replace("@", ""));
        row2.add(advertiseBtn);
        rows.add(row2);

        List<InlineKeyboardButton> row3 = new ArrayList<>();
        InlineKeyboardButton findCargoBtn = new InlineKeyboardButton();
        findCargoBtn.setText("📦 Yuk topish");
        findCargoBtn.setUrl("https://t.me/" + BOT_USERNAME.replace("@", ""));
        row3.add(findCargoBtn);
        rows.add(row3);

        markup.setKeyboard(rows);
        return markup;
    }

    private InlineKeyboardMarkup createDriverAdvertButtons(ResultSet rs) throws SQLException {
        String phone = rs.getString("phone");

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        if (phone != null && !phone.isEmpty()) {
            List<InlineKeyboardButton> row1 = new ArrayList<>();
            InlineKeyboardButton contactBtn = new InlineKeyboardButton();
            contactBtn.setText("📞 Bog'lanish");
            String cleanPhone = phone.replace("+", "").replaceAll("\\s", "");
            contactBtn.setUrl("https://t.me/" + cleanPhone);
            row1.add(contactBtn);
            rows.add(row1);
        }

        List<InlineKeyboardButton> row2 = new ArrayList<>();
        InlineKeyboardButton advertiseBtn = new InlineKeyboardButton();
        advertiseBtn.setText("📢 E'lon berish");
        advertiseBtn.setUrl("https://t.me/" + BOT_USERNAME.replace("@", ""));
        row2.add(advertiseBtn);
        rows.add(row2);

        List<InlineKeyboardButton> row3 = new ArrayList<>();
        InlineKeyboardButton findDriverBtn = new InlineKeyboardButton();
        findDriverBtn.setText("🚚 Haydovchi topish");
        findDriverBtn.setUrl("https://t.me/" + BOT_USERNAME.replace("@", ""));
        row3.add(findDriverBtn);
        rows.add(row3);

        markup.setKeyboard(rows);
        return markup;
    }

    private void sendCheckToAdmins(Long userId, Message photoMessage, int paymentId, int amount, String type, String advertType) {
        try {
            // To'lov ma'lumotlari
            String paymentInfo = String.format(
                    "🆕 *Yangi to'lov!*\n\n" +
                            "👤 Foydalanuvchi: %d\n" +
                            "💰 Miqdor: %,d so'm\n" +
                            "📝 Turi: %s\n" +
                            "🏷️ E'lon turi: %s\n" +
                            "🆔 Payment ID: %d\n" +
                            "📅 Vaqt: %s",
                    userId, amount, type, advertType, paymentId,
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"))
            );

            // Har bir admin uchun
            for (Long adminId : ADMIN_IDS) {
                // Chek rasmini forward qilish
                try {
                    ForwardMessage forward = new ForwardMessage();
                    forward.setChatId(adminId.toString());
                    forward.setFromChatId(photoMessage.getChatId().toString());
                    forward.setMessageId(photoMessage.getMessageId());
                    execute(forward);
                } catch (Exception e) {
                    System.err.println("Chek forward qilish xatosi: " + e.getMessage());
                }

                // Ma'lumotlar va tugmalar
                InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
                List<List<InlineKeyboardButton>> rows = new ArrayList<>();

                List<InlineKeyboardButton> row1 = new ArrayList<>();
                InlineKeyboardButton approveBtn = new InlineKeyboardButton();
                approveBtn.setText("✅ Tasdiqlash");
                approveBtn.setCallbackData("check_" + userId + "_approve_" + paymentId);
                row1.add(approveBtn);

                InlineKeyboardButton rejectBtn = new InlineKeyboardButton();
                rejectBtn.setText("❌ Rad etish");
                rejectBtn.setCallbackData("check_" + userId + "_reject_" + paymentId);
                row1.add(rejectBtn);

                List<InlineKeyboardButton> row2 = new ArrayList<>();
                InlineKeyboardButton profileBtn = new InlineKeyboardButton();
                profileBtn.setText("👤 Profilni ko'rish");
                profileBtn.setCallbackData("check_" + userId + "_profile_" + paymentId);
                row2.add(profileBtn);

                rows.add(row1);
                rows.add(row2);
                markup.setKeyboard(rows);

                SendMessage message = new SendMessage();
                message.setChatId(adminId.toString());
                message.setText(paymentInfo);
                message.setParseMode("Markdown");
                message.setReplyMarkup(markup);

                execute(message);
            }
        } catch (Exception e) {
            System.err.println("Adminlarga yuborish xatosi: " + e.getMessage());
        }
    }

    private void handleCheckCallback(Long adminId, String data) {
        try {
            String[] parts = data.split("_");
            Long userId = Long.parseLong(parts[1]);
            String action = parts[2];
            Integer paymentId = Integer.parseInt(parts[3]);

            switch (action) {
                case "approve":
                    approvePayment(adminId, userId, paymentId);
                    break;
                case "reject":
                    tempData.put(adminId + "_reject_user", String.valueOf(userId));
                    tempData.put(adminId + "_reject_payment", String.valueOf(paymentId));
                    userStates.put(adminId, "WAITING_REJECTION_REASON");
                    sendMessage(adminId, "❌ *To'lovni rad etish*\n\nRad etish sababini yozing:");
                    break;
                case "profile":
                    showUserProfile(adminId, userId);
                    break;
            }
        } catch (Exception e) {
            System.err.println("Check callback xatosi: " + e.getMessage());
        }
    }

    private void approvePayment(Long adminId, Long userId, Integer paymentId) {
        try {
            // 1. Payment ma'lumotlarini olish
            String type = null;
            String advertType = null;

            String selectSql = "SELECT type, advert_type FROM payments WHERE id = ?";
            try (PreparedStatement selectStmt = connection.prepareStatement(selectSql)) {
                selectStmt.setInt(1, paymentId);
                try (ResultSet rs = selectStmt.executeQuery()) {
                    if (rs.next()) {
                        type = rs.getString("type");
                        advertType = rs.getString("advert_type");
                    } else {
                        sendMessage(adminId, "❌ To'lov topilmadi! Payment ID: " + paymentId);
                        return;
                    }
                }
            }

            // 2. Payment ni tasdiqlash
            String updatePaymentSql = "UPDATE payments SET status = 'confirmed', admin_checked = true WHERE id = ?";
            try (PreparedStatement updateStmt = connection.prepareStatement(updatePaymentSql)) {
                updateStmt.setInt(1, paymentId);
                updateStmt.executeUpdate();
            }

            if ("e'lon".equals(type)) {
                int orderNumber = getNextOrderNumber();

                if ("driver".equals(advertType)) {
                    // 3. Oxirgi haydovchi e'lonini faollashtirish
                    String updateDriverSql = "UPDATE drivers SET is_paid = true, status = 'active', " +
                            "payment_status = 'confirmed', order_number = ?, " +
                            "expires_at = DATEADD('DAY', 5, CURRENT_TIMESTAMP) " +
                            "WHERE user_id = (SELECT id FROM users WHERE telegram_id = ?) " +
                            "AND status = 'pending' ORDER BY id DESC LIMIT 1";

                    try (PreparedStatement driverStmt = connection.prepareStatement(updateDriverSql)) {
                        driverStmt.setInt(1, orderNumber);
                        driverStmt.setLong(2, userId);
                        int rowsUpdated = driverStmt.executeUpdate();

                        if (rowsUpdated > 0) {
                            sendMessage(userId, "✅ To'lovingiz tasdiqlandi! Haydovchi e'loningiz faollashtirildi.\n\n" +
                                    "📍 *Eslatma:* E'loningiz 5 kun davomida guruhlarda chiqadi Asosiy gruh @Yuklar_bormi.");
                            sendMessage(adminId, "✅ Haydovchi e'loni tasdiqlandi! Foydalanuvchi: " + userId);

                            // 4. Guruhlarga joylash
                            postDriverAdvertToGroups(userId);
                        } else {
                            sendMessage(adminId, "❌ Haydovchi e'loni topilmadi! Foydalanuvchi e'lon bermagan.");

                            // E'lon topilmasa, foydalanuvchiga xabar
                            sendMessage(userId, "❌ Haydovchi e'loningiz topilmadi. Iltimos, qayta e'lon bering.");
                        }
                    }

                } else if ("cargo".equals(advertType)) {
                    // 3. Yuk e'lonini faollashtirish
                    String updateCargoSql = "UPDATE adverts SET is_paid = true, status = 'active', " +
                            "payment_status = 'confirmed', order_number = ?, " +
                            "expires_at = DATEADD('DAY', 5, CURRENT_TIMESTAMP) " +
                            "WHERE user_id = (SELECT id FROM users WHERE telegram_id = ?) " +
                            "AND status = 'pending' ORDER BY id DESC LIMIT 1";

                    try (PreparedStatement cargoStmt = connection.prepareStatement(updateCargoSql)) {
                        cargoStmt.setInt(1, orderNumber);
                        cargoStmt.setLong(2, userId);
                        int rowsUpdated = cargoStmt.executeUpdate();

                        if (rowsUpdated > 0) {
                            sendMessage(userId, "✅ To'lovingiz tasdiqlandi! Yuk e'loningiz faollashtirildi.\n\n" +
                                    "📍 *Eslatma:* E'loningiz 5 kun davomida guruhlarda chiqadi Asosiy gruh @Yuklar_bormi .");
                            sendMessage(adminId, "✅ Yuk e'loni tasdiqlandi! Foydalanuvchi: " + userId);

                            // 4. Guruhlarga joylash
                            postCargoAdvertToGroups(userId);
                        } else {
                            sendMessage(adminId, "❌ Yuk e'loni topilmadi! Foydalanuvchi e'lon bermagan.");

                            // E'lon topilmasa, foydalanuvchiga xabar
                            sendMessage(userId, "❌ Yuk e'loningiz topilmadi. Iltimos, qayta e'lon bering.");
                        }
                    }
                }
            } else if ("telegram_reklama".equals(type)) {
                sendMessage(userId, "✅ To'lovingiz tasdiqlandi! Telegram reklama paketingiz faollashtirildi Asosiy gruh @Yuklar_bormi.");
                sendMessage(adminId, "✅ Telegram reklama tasdiqlandi! Foydalanuvchi: " + userId);
            }

        } catch (SQLException e) {
            System.err.println("To'lov tasdiqlash xatosi: " + e.getMessage());
            sendMessage(adminId, "❌ Xatolik: " + e.getMessage());
        }
    }

    private int getNextOrderNumber() {
        try {
            String sql = "SELECT COALESCE(MAX(order_number), 0) + 1 FROM (SELECT order_number FROM adverts UNION SELECT order_number FROM drivers)";
            try (PreparedStatement pstmt = connection.prepareStatement(sql);
                 ResultSet rs = pstmt.executeQuery()) {

                if (rs.next()) {
                    return rs.getInt(1);
                }
                return 1;
            }
        } catch (SQLException e) {
            System.err.println("Order number olish xatosi: " + e.getMessage());
            return 1;
        }
    }

    // ==================== AVTOMATIK POSTING TIZIMI ====================

    private void startAutoPosting() {
        // Har 2 daqiqada e'lonlarni guruhlarga joylash
        scheduler.scheduleAtFixedRate(() -> {
            try {
                postNextCargoAdvert();
                Thread.sleep(30000); // 30 soniya kutish
                postNextDriverAdvert();
            } catch (Exception e) {
                System.err.println("Auto posting xatosi: " + e.getMessage());
            }
        }, 0, 2, TimeUnit.MINUTES);
    }

    private void postNextCargoAdvert() {
        try {
            String sql = "SELECT a.*, u.telegram_id FROM adverts a " +
                    "JOIN users u ON a.user_id = u.id " +
                    "WHERE a.status = 'active' AND a.is_paid = true " +
                    "AND a.expires_at > CURRENT_TIMESTAMP " +
                    "AND (a.last_posted IS NULL OR a.last_posted <= DATEADD('MINUTE', -30, CURRENT_TIMESTAMP)) " +
                    "ORDER BY a.last_posted ASC NULLS FIRST LIMIT 1";

            try (PreparedStatement pstmt = connection.prepareStatement(sql);
                 ResultSet rs = pstmt.executeQuery()) {

                if (rs.next()) {
                    Advert advert = extractAdvertFromResultSet(rs);
                    Long advertId = rs.getLong("id");
                    int orderNumber = rs.getInt("order_number");

                    // Barcha guruhlarga joylash
                    for (TelegramGroup group : groups) {
                        try {
                            String messageText = formatCargoAdvertMessage(advert, orderNumber);

                            SendMessage message = new SendMessage();
                            message.setChatId(group.groupId);
                            message.setText(messageText);
                            message.setParseMode("HTML");

                            // 3 ta tugma - Bog'lanish va Reklama berish
                            if (advert.getPhone() != null && !advert.getPhone().isEmpty()) {
                                InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
                                List<List<InlineKeyboardButton>> rows = new ArrayList<>();

                                List<InlineKeyboardButton> row1 = new ArrayList<>();
                                InlineKeyboardButton contactBtn = new InlineKeyboardButton();
                                contactBtn.setText("📞 Bog'lanish");
                                String phone = advert.getPhone().replace("+", "").replaceAll("\\s", "");
                                contactBtn.setUrl("https://t.me/" + phone);
                                row1.add(contactBtn);

                                List<InlineKeyboardButton> row2 = new ArrayList<>();
                                InlineKeyboardButton advertiseBtn = new InlineKeyboardButton();
                                advertiseBtn.setText("📢 E'lon berish");
                                advertiseBtn.setUrl("https://t.me/" + BOT_USERNAME.replace("@", ""));
                                row2.add(advertiseBtn);

                                List<InlineKeyboardButton> row3 = new ArrayList<>();
                                InlineKeyboardButton findCargoBtn = new InlineKeyboardButton();
                                findCargoBtn.setText("📦 Yuk topish");
                                findCargoBtn.setUrl("https://t.me/" + BOT_USERNAME.replace("@", ""));
                                row3.add(findCargoBtn);

                                rows.add(row1);
                                rows.add(row2);
                                rows.add(row3);
                                markup.setKeyboard(rows);
                                message.setReplyMarkup(markup);
                            }

                            execute(message);

                            // Guruh statistikasini yangilash
                            updateGroupMessageCount(group.groupId);

                            System.out.println("✅ Yuk e'loni yuborildi: " + group.groupId + " (Order: #" + orderNumber + ")");

                            Thread.sleep(1000); // 1 soniya kutish

                        } catch (Exception e) {
                            System.err.println("Guruhga yuborish xatosi: " + group.groupId + " - " + e.getMessage());
                        }
                    }

                    // E'lon statistikasini yangilash
                    updateAdvertPostCount(advertId);
                }

            }

        } catch (Exception e) {
            System.err.println("Yuk posting xatosi: " + e.getMessage());
        }
    }

    private void postNextDriverAdvert() {
        try {
            String sql = "SELECT d.*, u.telegram_id FROM drivers d " +
                    "JOIN users u ON d.user_id = u.id " +
                    "WHERE d.status = 'active' AND d.is_paid = true " +
                    "AND d.expires_at > CURRENT_TIMESTAMP " +
                    "AND (d.last_posted IS NULL OR d.last_posted <= DATEADD('MINUTE', -30, CURRENT_TIMESTAMP)) " +
                    "ORDER BY d.last_posted ASC NULLS FIRST LIMIT 1";

            try (PreparedStatement pstmt = connection.prepareStatement(sql);
                 ResultSet rs = pstmt.executeQuery()) {

                if (rs.next()) {
                    DriverAdvert advert = extractDriverAdvertFromResultSet(rs);
                    Long advertId = rs.getLong("id");
                    int orderNumber = rs.getInt("order_number");

                    // Barcha guruhlarga joylash
                    for (TelegramGroup group : groups) {
                        try {
                            String messageText = formatDriverAdvertMessage(advert, orderNumber);

                            SendMessage message = new SendMessage();
                            message.setChatId(group.groupId);
                            message.setText(messageText);
                            message.setParseMode("HTML");

                            // 3 ta tugma - Bog'lanish va Reklama berish
                            if (advert.getPhone() != null && !advert.getPhone().isEmpty()) {
                                InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
                                List<List<InlineKeyboardButton>> rows = new ArrayList<>();

                                List<InlineKeyboardButton> row1 = new ArrayList<>();
                                InlineKeyboardButton contactBtn = new InlineKeyboardButton();
                                contactBtn.setText("📞 Bog'lanish");
                                String phone = advert.getPhone().replace("+", "").replaceAll("\\s", "");
                                contactBtn.setUrl("https://t.me/" + phone);
                                row1.add(contactBtn);

                                List<InlineKeyboardButton> row2 = new ArrayList<>();
                                InlineKeyboardButton advertiseBtn = new InlineKeyboardButton();
                                advertiseBtn.setText("📢 E'lon berish");
                                advertiseBtn.setUrl("https://t.me/" + BOT_USERNAME.replace("@", ""));
                                row2.add(advertiseBtn);

                                List<InlineKeyboardButton> row3 = new ArrayList<>();
                                InlineKeyboardButton findDriverBtn = new InlineKeyboardButton();
                                findDriverBtn.setText("🚚 Haydovchi topish");
                                findDriverBtn.setUrl("https://t.me/" + BOT_USERNAME.replace("@", ""));
                                row3.add(findDriverBtn);

                                rows.add(row1);
                                rows.add(row2);
                                rows.add(row3);
                                markup.setKeyboard(rows);
                                message.setReplyMarkup(markup);
                            }

                            execute(message);

                            // Guruh statistikasini yangilash
                            updateGroupMessageCount(group.groupId);

                            System.out.println("✅ Haydovchi e'loni yuborildi: " + group.groupId + " (Order: #" + orderNumber + ")");

                            Thread.sleep(1000); // 1 soniya kutish

                        } catch (Exception e) {
                            System.err.println("Guruhga yuborish xatosi: " + group.groupId + " - " + e.getMessage());
                        }
                    }

                    // E'lon statistikasini yangilash
                    updateDriverPostCount(advertId);
                }

            }

        } catch (Exception e) {
            System.err.println("Haydovchi posting xatosi: " + e.getMessage());
        }
    }

    private void updateGroupMessageCount(String groupId) {
        try {
            String sql = "UPDATE telegram_groups SET message_count = message_count + 1 WHERE group_id = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, groupId);
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("Guruh statistikasini yangilash xatosi: " + e.getMessage());
        }
    }

    private void updateAdvertPostCount(Long advertId) {
        try {
            String sql = "UPDATE adverts SET post_count = COALESCE(post_count, 0) + 1, " +
                    "last_posted = CURRENT_TIMESTAMP WHERE id = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setLong(1, advertId);
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("Yuk e'loni statistikasini yangilash xatosi: " + e.getMessage());
        }
    }

    private void updateDriverPostCount(Long driverId) {
        try {
            String sql = "UPDATE drivers SET post_count = COALESCE(post_count, 0) + 1, " +
                    "last_posted = CURRENT_TIMESTAMP WHERE id = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setLong(1, driverId);
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("Haydovchi e'loni statistikasini yangilash xatosi: " + e.getMessage());
        }
    }

    private void postDriverAdvertToGroups(Long userId) {
        try {
            String sql = "SELECT d.* FROM drivers d " +
                    "JOIN users u ON d.user_id = u.id " +
                    "WHERE u.telegram_id = ? AND d.status = 'active' AND d.is_paid = true " +
                    "ORDER BY d.id DESC LIMIT 1";

            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setLong(1, userId);
                try (ResultSet rs = pstmt.executeQuery()) {

                    if (rs.next()) {
                        DriverAdvert advert = extractDriverAdvertFromResultSet(rs);
                        int orderNumber = rs.getInt("order_number");

                        // Barcha guruhlarga joylash
                        for (TelegramGroup group : groups) {
                            try {
                                String messageText = formatDriverAdvertMessage(advert, orderNumber);

                                SendMessage message = new SendMessage();
                                message.setChatId(group.groupId);
                                message.setText(messageText);
                                message.setParseMode("HTML");

                                // 3 ta tugma - Bog'lanish va Reklama berish
                                if (advert.getPhone() != null && !advert.getPhone().isEmpty()) {
                                    InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
                                    List<List<InlineKeyboardButton>> rows = new ArrayList<>();

                                    List<InlineKeyboardButton> row1 = new ArrayList<>();
                                    InlineKeyboardButton contactBtn = new InlineKeyboardButton();
                                    contactBtn.setText("📞 Bog'lanish");
                                    String phone = advert.getPhone().replace("+", "").replaceAll("\\s", "");
                                    contactBtn.setUrl("https://t.me/" + phone);
                                    row1.add(contactBtn);

                                    List<InlineKeyboardButton> row2 = new ArrayList<>();
                                    InlineKeyboardButton advertiseBtn = new InlineKeyboardButton();
                                    advertiseBtn.setText("📢 E'lon berish");
                                    advertiseBtn.setUrl("https://t.me/" + BOT_USERNAME.replace("@", ""));
                                    row2.add(advertiseBtn);

                                    List<InlineKeyboardButton> row3 = new ArrayList<>();
                                    InlineKeyboardButton findDriverBtn = new InlineKeyboardButton();
                                    findDriverBtn.setText("🚚 Haydovchi topish");
                                    findDriverBtn.setUrl("https://t.me/" + BOT_USERNAME.replace("@", ""));
                                    row3.add(findDriverBtn);

                                    rows.add(row1);
                                    rows.add(row2);
                                    rows.add(row3);
                                    markup.setKeyboard(rows);
                                    message.setReplyMarkup(markup);
                                }

                                execute(message);

                                // Statistikani yangilash
                                updateGroupMessageCount(group.groupId);

                                System.out.println("✅ Haydovchi e'loni guruhga yuborildi: " + group.groupId + " (Order: #" + orderNumber + ")");

                                Thread.sleep(1000); // 1 soniya kutish

                            } catch (Exception e) {
                                System.err.println("Guruhga yuborish xatosi: " + group.groupId + " - " + e.getMessage());
                            }
                        }

                        // E'lon statistikasini yangilash
                        updateDriverPostCount(rs.getLong("id"));
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("Haydovchi e'lonini guruhga joylash xatosi: " + e.getMessage());
        }
    }

    private void postCargoAdvertToGroups(Long userId) {
        try {
            String sql = "SELECT a.* FROM adverts a " +
                    "JOIN users u ON a.user_id = u.id " +
                    "WHERE u.telegram_id = ? AND a.status = 'active' AND a.is_paid = true " +
                    "ORDER BY a.id DESC LIMIT 1";

            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setLong(1, userId);
                try (ResultSet rs = pstmt.executeQuery()) {

                    if (rs.next()) {
                        Advert advert = extractAdvertFromResultSet(rs);
                        int orderNumber = rs.getInt("order_number");

                        // Barcha guruhlarga joylash
                        for (TelegramGroup group : groups) {
                            try {
                                String messageText = formatCargoAdvertMessage(advert, orderNumber);

                                SendMessage message = new SendMessage();
                                message.setChatId(group.groupId);
                                message.setText(messageText);
                                message.setParseMode("HTML");

                                // 3 ta tugma - Bog'lanish va Reklama berish
                                if (advert.getPhone() != null && !advert.getPhone().isEmpty()) {
                                    InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
                                    List<List<InlineKeyboardButton>> rows = new ArrayList<>();

                                    List<InlineKeyboardButton> row1 = new ArrayList<>();
                                    InlineKeyboardButton contactBtn = new InlineKeyboardButton();
                                    contactBtn.setText("📞 Bog'lanish");
                                    String phone = advert.getPhone().replace("+", "").replaceAll("\\s", "");
                                    contactBtn.setUrl("https://t.me/" + phone);
                                    row1.add(contactBtn);

                                    List<InlineKeyboardButton> row2 = new ArrayList<>();
                                    InlineKeyboardButton advertiseBtn = new InlineKeyboardButton();
                                    advertiseBtn.setText("📢 E'lon berish");
                                    advertiseBtn.setUrl("https://t.me/" + BOT_USERNAME.replace("@", ""));
                                    row2.add(advertiseBtn);

                                    List<InlineKeyboardButton> row3 = new ArrayList<>();
                                    InlineKeyboardButton findCargoBtn = new InlineKeyboardButton();
                                    findCargoBtn.setText("📦 Yuk topish");
                                    findCargoBtn.setUrl("https://t.me/" + BOT_USERNAME.replace("@", ""));
                                    row3.add(findCargoBtn);

                                    rows.add(row1);
                                    rows.add(row2);
                                    rows.add(row3);
                                    markup.setKeyboard(rows);
                                    message.setReplyMarkup(markup);
                                }

                                execute(message);

                                // Statistikani yangilash
                                updateGroupMessageCount(group.groupId);

                                System.out.println("✅ Yuk e'loni guruhga yuborildi: " + group.groupId + " (Order: #" + orderNumber + ")");

                                Thread.sleep(1000); // 1 soniya kutish

                            } catch (Exception e) {
                                System.err.println("Guruhga yuborish xatosi: " + group.groupId + " - " + e.getMessage());
                            }
                        }

                        // E'lon statistikasini yangilash
                        updateAdvertPostCount(rs.getLong("id"));
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("Yuk e'lonini guruhga joylash xatosi: " + e.getMessage());
        }
    }

    private String formatCargoAdvertMessage(Advert advert, int orderNumber) {
        return String.format(
                "<b>#%d 🚚 YUK TOPILDI</b>\n\n" +
                        "<b>📍 Qayerdan:</b> %s\n" +
                        "   %s\n" +
                        "<b>📍 Qayerga:</b> %s\n" +
                        "   %s\n" +
                        "<b>📦 Yuk turi:</b> %s\n" +
                        "<b>⚖️ Og'irligi:</b> %s\n" +
                        "<b>💰 Narxi:</b> %s\n" +
                        "<b>📞 Telefon:</b> %s\n" +
                        "<b>ℹ️ Qo'shimcha:</b> %s\n\n" +
                        "<i>#YukBot #Yuk #Transport</i>\n" +
                        "━━━━━━━━━━━━━━━━\n" +
                        "<b>📢 O'zingizning e'loningizni joylashtirish uchun:</b>",
                orderNumber,
                advert.getFromRegion() != null ? advert.getFromRegion() : "",
                advert.getFromAddress() != null ? advert.getFromAddress() : "",
                advert.getToRegion() != null ? advert.getToRegion() : "",
                advert.getToAddress() != null ? advert.getToAddress() : "",
                advert.getProductType() != null ? advert.getProductType() : "",
                advert.getWeight() != null ? advert.getWeight() : "",
                advert.getPrice() != null ? advert.getPrice() : "",
                advert.getPhone() != null ? advert.getPhone() : "",
                advert.getAdditionalInfo() != null ? advert.getAdditionalInfo() : ""
        );
    }

    private String formatDriverAdvertMessage(DriverAdvert advert, int orderNumber) {
        return String.format(
                "<b>#%d 🚚 HAYDOVCHI TOPILDI</b>\n\n" +
                        "<b>📍 Viloyat:</b> %s\n" +
                        "<b>📞 Telefon:</b> %s\n" +
                        "<b>🚗 Mashina:</b> %s\n" +
                        "<b>🔢 Raqami:</b> %s\n" +
                        "<b>⚖️ Sig'imi:</b> %s\n" +
                        "<b>ℹ️ Qo'shimcha:</b> %s\n\n" +
                        "<i>#YukBot #Haydovchi #Transport</i>\n" +
                        "━━━━━━━━━━━━━━━━\n" +
                        "<b>📢 O'zingizning e'loningizni joylashtirish uchun:</b>",
                orderNumber,
                advert.getRegion() != null ? advert.getRegion() : "",
                advert.getPhone() != null ? advert.getPhone() : "",
                advert.getCarModel() != null ? advert.getCarModel() : "",
                advert.getCarNumber() != null ? advert.getCarNumber() : "",
                advert.getCarCapacity() != null ? advert.getCarCapacity() : "",
                advert.getAdditionalInfo() != null ? advert.getAdditionalInfo() : ""
        );
    }

    // ==================== Mening e'lonlarim ====================

    private void showMyAdverts(Long chatId) {
        List<Advert> cargoAdverts = getUserCargoAdverts(chatId);
        List<DriverAdvert> driverAdverts = getUserDriverAdverts(chatId);

        if (cargoAdverts.isEmpty() && driverAdverts.isEmpty()) {
            sendMessage(chatId, "📭 Sizda hali e'lonlar mavjud emas.");
            return;
        }

        StringBuilder text = new StringBuilder();
        text.append("📋 *Mening e'lonlarim:*\n\n");

        if (!cargoAdverts.isEmpty()) {
            text.append("📦 *Yuk e'lonlari:*\n");
            for (int i = 0; i < cargoAdverts.size(); i++) {
                Advert advert = cargoAdverts.get(i);
                text.append(String.format(
                        "%d. *%s → %s*\n" +
                                "   📦 %s\n" +
                                "   ⚖️ %s\n" +
                                "   💰 %s\n" +
                                "   📞 %s\n" +
                                "   🏷️ %s\n" +
                                "   📊 Joylangan: %d marta\n\n",
                        i + 1,
                        advert.getFromAddress() != null ? advert.getFromAddress() : "",
                        advert.getToAddress() != null ? advert.getToAddress() : "",
                        advert.getProductType() != null ? advert.getProductType() : "",
                        advert.getWeight() != null ? advert.getWeight() : "",
                        advert.getPrice() != null ? advert.getPrice() : "",
                        advert.getPhone() != null ? advert.getPhone() : "",
                        advert.getStatus() != null ? advert.getStatus() : "pending",
                        advert.getPostCount()
                ));
            }
        }

        if (!driverAdverts.isEmpty()) {
            text.append("\n🚚 *Haydovchi e'lonlari:*\n");
            for (int i = 0; i < driverAdverts.size(); i++) {
                DriverAdvert advert = driverAdverts.get(i);
                text.append(String.format(
                        "%d. *%s*\n" +
                                "   🚗 %s\n" +
                                "   🔢 %s\n" +
                                "   ⚖️ %s\n" +
                                "   📞 %s\n" +
                                "   🏷️ %s\n" +
                                "   📊 Joylangan: %d marta\n\n",
                        i + 1,
                        advert.getRegion() != null ? advert.getRegion() : "",
                        advert.getCarModel() != null ? advert.getCarModel() : "",
                        advert.getCarNumber() != null ? advert.getCarNumber() : "",
                        advert.getCarCapacity() != null ? advert.getCarCapacity() : "",
                        advert.getPhone() != null ? advert.getPhone() : "",
                        advert.getStatus() != null ? advert.getStatus() : "pending",
                        advert.getPostCount()
                ));
            }
        }

        sendMessage(chatId, text.toString());
    }

    private List<Advert> getUserCargoAdverts(Long telegramId) {
        List<Advert> adverts = new ArrayList<>();
        try {
            String sql = "SELECT * FROM adverts WHERE user_id = " +
                    "(SELECT id FROM users WHERE telegram_id = ?) ORDER BY id DESC";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setLong(1, telegramId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        adverts.add(extractAdvertFromResultSet(rs));
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Yuk e'lonlarini olish xatosi: " + e.getMessage());
        }
        return adverts;
    }

    private List<DriverAdvert> getUserDriverAdverts(Long telegramId) {
        List<DriverAdvert> adverts = new ArrayList<>();
        try {
            String sql = "SELECT * FROM drivers WHERE user_id = " +
                    "(SELECT id FROM users WHERE telegram_id = ?) ORDER BY id DESC";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setLong(1, telegramId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        adverts.add(extractDriverAdvertFromResultSet(rs));
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Haydovchi e'lonlarini olish xatosi: " + e.getMessage());
        }
        return adverts;
    }

    private Advert extractAdvertFromResultSet(ResultSet rs) throws SQLException {
        Advert advert = new Advert();
        advert.setFromRegion(rs.getString("from_region"));
        advert.setFromAddress(rs.getString("from_address"));
        advert.setToRegion(rs.getString("to_region"));
        advert.setToAddress(rs.getString("to_address"));
        advert.setProductType(rs.getString("product_type"));
        advert.setWeight(rs.getString("weight"));
        advert.setPhone(rs.getString("phone"));
        advert.setPrice(rs.getString("price"));
        advert.setAdditionalInfo(rs.getString("additional_info"));
        advert.setStatus(rs.getString("status"));
        advert.setPostCount(rs.getInt("post_count"));
        return advert;
    }

    private DriverAdvert extractDriverAdvertFromResultSet(ResultSet rs) throws SQLException {
        DriverAdvert advert = new DriverAdvert();
        advert.setRegion(rs.getString("region"));
        advert.setPhone(rs.getString("phone"));
        advert.setCarModel(rs.getString("car_model"));
        advert.setCarNumber(rs.getString("car_number"));
        advert.setCarCapacity(rs.getString("car_capacity"));
        advert.setAdditionalInfo(rs.getString("additional_info"));
        advert.setStatus(rs.getString("status"));
        advert.setPostCount(rs.getInt("post_count"));
        return advert;
    }

    // ==================== ADMIN PANEL ====================

    private void sendAdminPanel(Long chatId) {
        String text = "👨‍💼 *Admin Panel*\n\nKerakli bo'limni tanlang:";

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton statsBtn = new InlineKeyboardButton();
        statsBtn.setText("📊 Statistika");
        statsBtn.setCallbackData("admin_stats");
        row1.add(statsBtn);

        InlineKeyboardButton revenueBtn = new InlineKeyboardButton();
        revenueBtn.setText(paymentSystemEnabled ? "💰 To'lov tizimi (ON)" : "💰 To'lov tizimi (OFF)");
        revenueBtn.setCallbackData("admin_revenue");
        row1.add(revenueBtn);

        // YANGI: Foydalanuvchilar uchun reklama tugmasi
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        InlineKeyboardButton userAdBtn = new InlineKeyboardButton();
        userAdBtn.setText("📢 Foydalanuvchilar uchun reklama");
        userAdBtn.setCallbackData("admin_user_ad");
        row2.add(userAdBtn);

        InlineKeyboardButton groupBtn = new InlineKeyboardButton();
        groupBtn.setText("➕ Yangi guruh");
        groupBtn.setCallbackData("admin_add_group");
        row2.add(groupBtn);

        List<InlineKeyboardButton> row3 = new ArrayList<>();
        InlineKeyboardButton groupsBtn = new InlineKeyboardButton();
        groupsBtn.setText("📋 Guruhlar ro'yxati");
        groupsBtn.setCallbackData("admin_groups");
        row3.add(groupsBtn);

        InlineKeyboardButton broadcastBtn = new InlineKeyboardButton();
        broadcastBtn.setText("📢 Foydalanuvchilarga");
        broadcastBtn.setCallbackData("admin_broadcast");
        row3.add(broadcastBtn);

        List<InlineKeyboardButton> row4 = new ArrayList<>();
        InlineKeyboardButton pendingBtn = new InlineKeyboardButton();
        pendingBtn.setText("⏳ Kutayotgan to'lovlar");
        pendingBtn.setCallbackData("admin_pending");
        row4.add(pendingBtn);

        InlineKeyboardButton backBtn = new InlineKeyboardButton();
        backBtn.setText("🔙 Orqaga");
        backBtn.setCallbackData("back_to_main");
        row4.add(backBtn);

        rows.add(row1);
        rows.add(row2);
        rows.add(row3);
        rows.add(row4);
        markup.setKeyboard(rows);

        sendMessage(chatId, text, markup);
    }

    private void handleAdminCallback(Long chatId, String data) {
        switch (data) {
            case "admin_stats":
                showStatistics(chatId);
                break;
            case "admin_revenue":
                showRevenueManagement(chatId);
                break;
            case "admin_user_ad":
                startUserAdvertisement(chatId);
                break;
            case "admin_add_group":
                sendMessage(chatId, "➕ *Yangi guruh qo'shish:*\n\nGuruh havolasini yuboring:");
                userStates.put(chatId, "WAITING_GROUP_LINK");
                break;
            case "admin_broadcast":
                sendMessage(chatId, "📢 *Foydalanuvchilarga xabar:*\n\nYubormoqchi bo'lgan xabaringizni yozing:");
                userStates.put(chatId, "WAITING_BROADCAST");
                break;
            case "admin_groups":
                showGroupsList(chatId);
                break;
            case "admin_pending":
                showPendingPayments(chatId);
                break;
            case "admin_enable_payments":
                enablePaymentSystem(chatId);
                break;
            case "admin_disable_payments":
                disablePaymentSystem(chatId);
                break;
            case "admin_back":
                sendAdminPanel(chatId);
                break;
        }
    }

    // ==================== DAROMADNI BOSHQAISH ====================

    private void showRevenueManagement(Long chatId) {
        String status = paymentSystemEnabled ? "✅ YOQILGAN" : "❌ O'CHIRILGAN";
        String text = String.format(
                "💰 *To'lov tizimini boshqarish*\n\n" +
                        "Hozirgi holat: %s\n\n" +
                        "To'lov tizimini yoqish yoki o'chirish mumkin:\n\n" +
                        "🔴 *O'chirish:* To'lov tizimi o'chiriladi\n" +
                        "   • E'lonlar TEKIN faollashadi\n" +
                        "   • Ruxsat berilgan ham TEKIN\n" +
                        "   • To'lov olinmaydi\n\n" +
                        "🟢 *Yoqish:* To'lov tizimi yoqiladi\n" +
                        "   • E'lonlar to'lov evaziga faollashadi\n" +
                        "   • Ruxsat berilgan ham pullik\n" +
                        "   • Normal to'lov jarayoni",
                status
        );

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton enableBtn = new InlineKeyboardButton();
        enableBtn.setText("🟢 To'lovni yoqish");
        enableBtn.setCallbackData("admin_enable_payments");
        row1.add(enableBtn);

        InlineKeyboardButton disableBtn = new InlineKeyboardButton();
        disableBtn.setText("🔴 To'lovni o'chirish");
        disableBtn.setCallbackData("admin_disable_payments");
        row1.add(disableBtn);

        List<InlineKeyboardButton> row2 = new ArrayList<>();
        InlineKeyboardButton backBtn = new InlineKeyboardButton();
        backBtn.setText("🔙 Orqaga");
        backBtn.setCallbackData("admin_back");
        row2.add(backBtn);

        rows.add(row1);
        rows.add(row2);
        markup.setKeyboard(rows);

        sendMessage(chatId, text, markup);
    }

    private void disablePaymentSystem(Long chatId) {
        paymentSystemEnabled = false;
        sendMessage(chatId, "✅ *To'lov tizimi O'CHIRILDI!*\n\n" +
                "Endi foydalanuvchilar:\n" +
                "• E'lonlarni TEKIN joylashtira oladi\n" +
                "• Ruxsat berilgan paketlar TEKIN ishlaydi\n" +
                "• To'lov olinmaydi");

        // Barcha adminlarga xabar berish
        for (Long adminId : ADMIN_IDS) {
            if (!adminId.equals(chatId)) {
                sendMessage(adminId, "⚠️ *Diqqat!*\n\n" +
                        "To'lov tizimi " + chatId + " admin tomonidan O'CHIRILDI!\n" +
                        "Endi barcha e'lonlar tekin joylashadi.");
            }
        }
    }

    private void enablePaymentSystem(Long chatId) {
        paymentSystemEnabled = true;
        sendMessage(chatId, "✅ *To'lov tizimi YOQILDI!*\n\n" +
                "Endi foydalanuvchilar:\n" +
                "• E'lonlarni to'lov evaziga joylashtiradi\n" +
                "• Ruxsat berilgan paketlar pullik bo'ladi\n" +
                "• Normal to'lov jarayoni tiklandi");

        // Barcha adminlarga xabar berish
        for (Long adminId : ADMIN_IDS) {
            if (!adminId.equals(chatId)) {
                sendMessage(adminId, "ℹ️ *Xabar!*\n\n" +
                        "To'lov tizimi " + chatId + " admin tomonidan YOQILDI!\n" +
                        "Endi barcha e'lonlar pullik bo'ladi.");
            }
        }
    }

    // ==================== TEKIN E'LON BERISH ====================

    private void activateDriverAdvertForFree(Long chatId) {
        try {
            DriverAdvert advert = tempDriverAdverts.get(chatId);
            if (advert != null) {
                // 1. E'lonni saqlash
                saveDriverAdvert(chatId, advert);

                // 2. E'lonni faollashtirish (tekin)
                String sql = "UPDATE drivers SET is_paid = true, status = 'active', " +
                        "payment_status = 'confirmed', order_number = ?, " +
                        "expires_at = DATEADD('DAY', 5, CURRENT_TIMESTAMP) " +
                        "WHERE user_id = (SELECT id FROM users WHERE telegram_id = ?) " +
                        "AND status = 'pending' ORDER BY id DESC LIMIT 1";

                try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                    int orderNumber = getNextOrderNumber();
                    pstmt.setInt(1, orderNumber);
                    pstmt.setLong(2, chatId);
                    int rowsUpdated = pstmt.executeUpdate();

                    if (rowsUpdated > 0) {
                        sendMessage(chatId, "✅ E'loningiz TEKIN faollashtirildi!\n\n" +
                                "📍 *Eslatma:* E'loningiz 5 kun davomida guruhlarda chiqadi.\n" +
                                "🎉 Bu vaqtinchalik imkoniyat uchun rahmat!");

                        // 3. Guruhlarga joylash
                        postDriverAdvertToGroups(chatId);

                        // 4. Adminlarga xabar
                        for (Long adminId : ADMIN_IDS) {
                            sendMessage(adminId, "ℹ️ *Yangi TEKIN e'lon:*\n\n" +
                                    "👤 Foydalanuvchi: " + chatId + "\n" +
                                    "🚗 Haydovchi e'lon TEKIN faollashtirildi\n" +
                                    "📅 Vaqt: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")));
                        }
                    }
                }

                // 5. Tozalash
                tempDriverAdverts.remove(chatId);
                tempData.remove(chatId + "_driver_region");
            }
        } catch (SQLException e) {
            System.err.println("TEKIN haydovchi faollashtirish xatosi: " + e.getMessage());
            sendMessage(chatId, "❌ Xatolik yuz berdi. Iltimos, qayta urinib ko'ring.");
        }
    }

    private void activateCargoAdvertForFree(Long chatId) {
        try {
            Advert advert = tempAdverts.get(chatId);
            if (advert != null) {
                // 1. E'lonni saqlash
                saveCargoAdvert(chatId, advert);

                // 2. E'lonni faollashtirish (tekin)
                String sql = "UPDATE adverts SET is_paid = true, status = 'active', " +
                        "payment_status = 'confirmed', order_number = ?, " +
                        "expires_at = DATEADD('DAY', 5, CURRENT_TIMESTAMP) " +
                        "WHERE user_id = (SELECT id FROM users WHERE telegram_id = ?) " +
                        "AND status = 'pending' ORDER BY id DESC LIMIT 1";

                try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                    int orderNumber = getNextOrderNumber();
                    pstmt.setInt(1, orderNumber);
                    pstmt.setLong(2, chatId);
                    int rowsUpdated = pstmt.executeUpdate();

                    if (rowsUpdated > 0) {
                        sendMessage(chatId, "✅ E'loningiz TEKIN faollashtirildi!\n\n" +
                                "📍 *Eslatma:* E'loningiz 5 kun davomida guruhlarda chiqadi.\n" +
                                "🎉 Bu vaqtinchalik imkoniyat uchun rahmat!");

                        // 3. Guruhlarga joylash
                        postCargoAdvertToGroups(chatId);

                        // 4. Adminlarga xabar
                        for (Long adminId : ADMIN_IDS) {
                            sendMessage(adminId, "ℹ️ *Yangi TEKIN e'lon:*\n\n" +
                                    "👤 Foydalanuvchi: " + chatId + "\n" +
                                    "📦 Yuk e'lon TEKIN faollashtirildi\n" +
                                    "📅 Vaqt: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")));
                        }
                    }
                }

                // 5. Tozalash
                tempAdverts.remove(chatId);
                tempData.remove(chatId + "_from_region");
                tempData.remove(chatId + "_to_region");
            }
        } catch (SQLException e) {
            System.err.println("TEKIN yuk faollashtirish xatosi: " + e.getMessage());
            sendMessage(chatId, "❌ Xatolik yuz berdi. Iltimos, qayta urinib ko'ring.");
        }
    }

    // ==================== TELEGRAM REKLAMA PAKETLARI ====================

    private void showTelegramPackages(Long chatId) {
        if (paymentSystemEnabled) {
            // Normal to'lov tizimi
            String text = "🤖 *Telegram Reklama Paketlari*\n\n" +
                    "Sizning e'loningizni 20+ kanalga avtomatik joylashtiramiz!\n\n" +
                    "📊 *Paketlar:*\n" +
                    "1️⃣ 20 kanal, 50 marta - 30,000 so'm\n" +
                    "2️⃣ 20 kanal, 100 marta - 60,000 so'm\n" +
                    "3️⃣ 20 kanal, 200 marta - 100,000 so'm\n" +
                    "4️⃣ 20 kanal, 500 marta - 200,000 so'm\n\n" +
                    "Har 2 daqiqada 1 marta tashlanadi.";

            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();

            String[] packages = {"50", "100", "200", "500"};
            int[] prices = {30000, 60000, 100000, 200000};

            for (int i = 0; i < packages.length; i++) {
                List<InlineKeyboardButton> row = new ArrayList<>();
                InlineKeyboardButton btn = new InlineKeyboardButton();
                btn.setText(packages[i] + " marta - " + String.format("%,d", prices[i]) + " so'm");
                btn.setCallbackData("tg_package_" + packages[i] + "_" + prices[i]);
                row.add(btn);
                rows.add(row);
            }

            markup.setKeyboard(rows);

            sendMessage(chatId, text, markup);
        } else {
            // To'lov tizimi o'chirilgan - TEKIN
            String text = "🤖 *Telegram Reklama Paketlari*\n\n" +
                    "Sizning e'loningizni 20+ kanalga avtomatik joylashtiramiz!\n\n" +
                    "🎉 *HURMATLI FOYDALANUVCHI!*\n" +
                    "To'lov tizimi vaqtincha o'chirilgan.\n" +
                    "Barcha paketlar TEKIN ishlaydi!\n\n" +
                    "📊 *Paketlar:*\n" +
                    "1️⃣ 20 kanal, 50 marta - TEKIN\n" +
                    "2️⃣ 20 kanal, 100 marta - TEKIN\n" +
                    "3️⃣ 20 kanal, 200 marta - TEKIN\n" +
                    "4️⃣ 20 kanal, 500 marta - TEKIN\n\n" +
                    "📝 *Diqqat:* Bu vaqtinchalik imkoniyat. Keyinchalik to'lov talab qilinishi mumkin.";

            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();

            String[] packages = {"50", "100", "200", "500"};

            for (int i = 0; i < packages.length; i++) {
                List<InlineKeyboardButton> row = new ArrayList<>();
                InlineKeyboardButton btn = new InlineKeyboardButton();
                btn.setText(packages[i] + " marta - TEKIN");
                btn.setCallbackData("tg_free_package_" + packages[i]);
                row.add(btn);
                rows.add(row);
            }

            markup.setKeyboard(rows);

            sendMessage(chatId, text, markup);
        }
    }

    private void activateTelegramPackageForFree(Long chatId, String count) {
        try {
            int messageCount = Integer.parseInt(count);

            // Bu yerda Telegram reklama paketini saqlash logikasi
            // Sizning loyihangizga qarab, bu joyni to'ldirishingiz kerak

            sendMessage(chatId, String.format(
                    "✅ Telegram reklama paketingiz TEKIN faollashtirildi!\n\n" +
                            "📊 Paket: %s marta\n" +
                            "🎉 Bu vaqtinchalik imkoniyat uchun rahmat!\n\n" +
                            "Sizning e'loningiz 20+ kanalga %s marta avtomatik joylashtiriladi.",
                    count, count
            ));

            // Adminlarga xabar
            for (Long adminId : ADMIN_IDS) {
                sendMessage(adminId, "ℹ️ *Yangi TEKIN Telegram reklama:*\n\n" +
                        "👤 Foydalanuvchi: " + chatId + "\n" +
                        "📊 Paket: " + count + " marta\n" +
                        "💰 Narx: TEKIN\n" +
                        "📅 Vaqt: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")));
            }

        } catch (Exception e) {
            System.err.println("TEKIN Telegram paketi faollashtirish xatosi: " + e.getMessage());
            sendMessage(chatId, "❌ Xatolik yuz berdi. Iltimos, qayta urinib ko'ring.");
        }
    }

    // ==================== STATISTIKA VA BOSHQA ADMIN FUNKSIYALARI ====================

    private void showStatistics(Long chatId) {
        Map<String, Object> stats = getStatistics();

        String text = String.format(
                "📊 *Bot statistikasi:*\n\n" +
                        "👥 Foydalanuvchilar: %d\n" +
                        "📦 Yuk e'lonlari: %d\n" +
                        "🚚 Haydovchi e'lonlari: %d\n" +
                        "💰 Daromad: %,.0f so'm\n" +
                        "📈 Guruhlar: %d\n" +
                        "🔄 Faol reklamalar: %d\n" +
                        "⏰ Server vaqti: %s",
                (int) stats.get("users"),
                (int) stats.get("cargo_ads"),
                (int) stats.get("driver_ads"),
                (double) stats.get("income"),
                (int) stats.get("groups"),
                (int) stats.get("active_ads"),
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"))
        );

        sendMessage(chatId, text);
    }

    private Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        try (Statement stmt = connection.createStatement()) {

            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM users");
            if (rs.next()) stats.put("users", rs.getInt(1));

            rs = stmt.executeQuery("SELECT COUNT(*) FROM adverts");
            if (rs.next()) stats.put("cargo_ads", rs.getInt(1));

            rs = stmt.executeQuery("SELECT COUNT(*) FROM drivers");
            if (rs.next()) stats.put("driver_ads", rs.getInt(1));

            rs = stmt.executeQuery("SELECT COALESCE(SUM(amount), 0) FROM payments WHERE status = 'confirmed'");
            if (rs.next()) stats.put("income", rs.getDouble(1));

            rs = stmt.executeQuery("SELECT COUNT(*) FROM telegram_groups WHERE is_active = true");
            if (rs.next()) stats.put("groups", rs.getInt(1));

            int activeCargo = 0;
            rs = stmt.executeQuery("SELECT COUNT(*) FROM adverts WHERE status = 'active' AND expires_at > CURRENT_TIMESTAMP");
            if (rs.next()) activeCargo = rs.getInt(1);

            int activeDriver = 0;
            rs = stmt.executeQuery("SELECT COUNT(*) FROM drivers WHERE status = 'active' AND expires_at > CURRENT_TIMESTAMP");
            if (rs.next()) activeDriver = rs.getInt(1);

            stats.put("active_ads", activeCargo + activeDriver);

        } catch (SQLException e) {
            System.err.println("Statistika olish xatosi: " + e.getMessage());
        }
        return stats;
    }

    private void showGroupsList(Long adminId) {
        StringBuilder text = new StringBuilder();
        text.append("📋 *Faol guruhlar ro'yxati:*\n\n");

        for (int i = 0; i < groups.size(); i++) {
            TelegramGroup group = groups.get(i);
            text.append(String.format(
                    "%d. %s\n" +
                            "   📎 %s\n" +
                            "   📊 Xabarlar: %d\n\n",
                    i + 1,
                    group.groupId,
                    group.link,
                    group.messageCount
            ));
        }

        if (groups.isEmpty()) {
            text.append("📭 Hozircha guruhlar yo'q");
        }

        sendMessage(adminId, text.toString());
    }

    private void showPendingPayments(Long adminId) {
        try {
            String sql = "SELECT p.*, u.telegram_id, u.first_name FROM payments p " +
                    "JOIN users u ON p.user_id = u.id " +
                    "WHERE p.status = 'pending' ORDER BY p.created_at DESC";

            try (PreparedStatement pstmt = connection.prepareStatement(sql);
                 ResultSet rs = pstmt.executeQuery()) {

                StringBuilder text = new StringBuilder();
                text.append("⏳ *Kutayotgan to'lovlar:*\n\n");

                int count = 1;
                while (rs.next()) {
                    text.append(String.format(
                            "%d. Foydalanuvchi: %s (%d)\n" +
                                    "   💰 Miqdor: %,.0f so'm\n" +
                                    "   📝 Turi: %s\n" +
                                    "   🏷️ E'lon turi: %s\n" +
                                    "   🆔 Payment ID: %d\n" +
                                    "   📅 %s\n\n",
                            count++,
                            rs.getString("first_name"),
                            rs.getLong("telegram_id"),
                            rs.getDouble("amount"),
                            rs.getString("type"),
                            rs.getString("advert_type"),
                            rs.getInt("id"),
                            rs.getTimestamp("created_at").toLocalDateTime()
                                    .format(DateTimeFormatter.ofPattern("dd-MM HH:mm"))
                    ));
                }

                if (count == 1) {
                    text.append("✅ Kutayotgan to'lovlar yo'q");
                }

                sendMessage(adminId, text.toString());
            }

        } catch (SQLException e) {
            System.err.println("Kutayotgan to'lovlarni olish xatosi: " + e.getMessage());
        }
    }

    private void addGroup(Long adminId, String link) {
        try {
            if (!link.contains("t.me/")) {
                sendMessage(adminId, "❌ Noto'g'ri havola! Telegram havolasi bo'lishi kerak.");
                return;
            }

            String groupId = "@" + link.substring(link.lastIndexOf("/") + 1);

            // Asosiy guruhni qayta qo'shmaslik uchun tekshirish
            if (groupId.equals(MAIN_GROUP_ID)) {
                sendMessage(adminId, "❌ Bu asosiy guruh allaqachon mavjud!");
                return;
            }

            String sql = "INSERT INTO telegram_groups (group_id, name, link) VALUES (?, ?, ?)";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, groupId);
                pstmt.setString(2, "Lagistika Guruhi");
                pstmt.setString(3, link);
                pstmt.executeUpdate();
            }

            TelegramGroup group = new TelegramGroup();
            group.groupId = groupId;
            group.link = link;
            group.name = "Lagistika Guruhi";
            groups.add(group);

            sendMessage(adminId, "✅ Guruh qo'shildi!\n\nID: " + groupId + "\nHavola: " + link);
            userStates.remove(adminId);

        } catch (SQLException e) {
            System.err.println("Guruh qo'shish xatosi: " + e.getMessage());
            sendMessage(adminId, "❌ Xatolik: " + e.getMessage());
        }
    }

    private void sendBroadcastToAllUsers(Long adminId, String message) {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT telegram_id FROM users")) {

            int sent = 0;
            int failed = 0;

            while (rs.next()) {
                Long userId = rs.getLong("telegram_id");
                try {
                    sendMessage(userId, "📢 *Botdan xabar:*\n\n" + message);
                    sent++;
                    Thread.sleep(50);
                } catch (Exception e) {
                    failed++;
                }
            }

            sendMessage(adminId, String.format(
                    "✅ Broadcast yakunlandi!\n\n" +
                            "✅ Yuborildi: %d\n" +
                            "❌ Yuborilmadi: %d\n" +
                            "📊 Jami: %d",
                    sent, failed, sent + failed
            ));

            userStates.remove(adminId);

        } catch (SQLException e) {
            System.err.println("Broadcast xatosi: " + e.getMessage());
        }
    }

    // ==================== HELPER METHODS ====================

    private void saveUser(Long telegramId, String firstName, String username) {
        try {
            // Avval foydalanuvchi mavjudligini tekshirish
            String checkSql = "SELECT id FROM users WHERE telegram_id = ?";
            try (PreparedStatement checkStmt = connection.prepareStatement(checkSql)) {
                checkStmt.setLong(1, telegramId);
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next()) {
                        // Foydalanuvchi mavjud, yangilash
                        String updateSql = "UPDATE users SET first_name = ?, username = ? WHERE telegram_id = ?";
                        try (PreparedStatement updateStmt = connection.prepareStatement(updateSql)) {
                            updateStmt.setString(1, firstName);
                            updateStmt.setString(2, username != null ? username : "");
                            updateStmt.setLong(3, telegramId);
                            updateStmt.executeUpdate();
                        }
                    } else {
                        // Yangi foydalanuvchi qo'shish
                        String insertSql = "INSERT INTO users (telegram_id, first_name, username) VALUES (?, ?, ?)";
                        try (PreparedStatement insertStmt = connection.prepareStatement(insertSql)) {
                            insertStmt.setLong(1, telegramId);
                            insertStmt.setString(2, firstName);
                            insertStmt.setString(3, username != null ? username : "");
                            insertStmt.executeUpdate();
                        }
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Foydalanuvchi saqlash xatosi: " + e.getMessage());
        }
    }

    private void saveCargoAdvert(Long telegramId, Advert advert) {
        try {
            String sql = "INSERT INTO adverts (user_id, from_region, from_address, to_region, to_address, " +
                    "product_type, weight, phone, price, additional_info, type, status) " +
                    "VALUES ((SELECT id FROM users WHERE telegram_id = ?), ?, ?, ?, ?, ?, ?, ?, ?, ?, 'yuk_berish', 'pending')";

            try (PreparedStatement pstmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                pstmt.setLong(1, telegramId);
                pstmt.setString(2, advert.getFromRegion());
                pstmt.setString(3, advert.getFromAddress());
                pstmt.setString(4, advert.getToRegion());
                pstmt.setString(5, advert.getToAddress());
                pstmt.setString(6, advert.getProductType());
                pstmt.setString(7, advert.getWeight());
                pstmt.setString(8, advert.getPhone());
                pstmt.setString(9, advert.getPrice());
                pstmt.setString(10, advert.getAdditionalInfo());
                pstmt.executeUpdate();

                // E'lon ID sini olish
                try (ResultSet rs = pstmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        long advertId = rs.getLong(1);
                        System.out.println("✅ Yuk e'loni saqlandi: " + telegramId + " (ID: " + advertId + ")");
                    }
                }
            }

        } catch (SQLException e) {
            System.err.println("Yuk e'lonini saqlash xatosi: " + e.getMessage());
        }
    }

    private void saveDriverAdvert(Long telegramId, DriverAdvert advert) {
        try {
            String sql = "INSERT INTO drivers (user_id, region, phone, car_model, car_number, car_capacity, additional_info, type, status) " +
                    "VALUES ((SELECT id FROM users WHERE telegram_id = ?), ?, ?, ?, ?, ?, ?, 'yuk_olish', 'pending')";

            try (PreparedStatement pstmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                pstmt.setLong(1, telegramId);
                pstmt.setString(2, advert.getRegion());
                pstmt.setString(3, advert.getPhone());
                pstmt.setString(4, advert.getCarModel());
                pstmt.setString(5, advert.getCarNumber());
                pstmt.setString(6, advert.getCarCapacity());
                pstmt.setString(7, advert.getAdditionalInfo());
                pstmt.executeUpdate();

                // E'lon ID sini olish
                try (ResultSet rs = pstmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        long advertId = rs.getLong(1);
                        System.out.println("✅ Haydovchi e'loni saqlandi: " + telegramId + " (ID: " + advertId + ")");
                    }
                }
            }

        } catch (SQLException e) {
            System.err.println("Haydovchi e'lonini saqlash xatosi: " + e.getMessage());
        }
    }

    private int savePayment(Long telegramId, int amount, String type, String advertType, String photoId) throws SQLException {
        String sql = "INSERT INTO payments (user_id, amount, type, advert_type, check_photo_id, status) VALUES " +
                "((SELECT id FROM users WHERE telegram_id = ?), ?, ?, ?, ?, 'pending')";

        try (PreparedStatement pstmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setLong(1, telegramId);
            pstmt.setInt(2, amount);
            pstmt.setString(3, type);
            pstmt.setString(4, advertType);
            pstmt.setString(5, photoId);
            pstmt.executeUpdate();

            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    int paymentId = rs.getInt(1);
                    System.out.println("✅ To'lov saqlandi: " + telegramId + " - " + amount + " (ID: " + paymentId + ")");
                    return paymentId;
                }
            }
        }
        return 0;
    }

    private void saveFeedback(Long telegramId, String message, String type) {
        try {
            String sql = "INSERT INTO feedback (user_id, message, type) " +
                    "VALUES ((SELECT id FROM users WHERE telegram_id = ?), ?, ?)";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setLong(1, telegramId);
                pstmt.setString(2, message);
                pstmt.setString(3, type);
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("Fikr saqlash xatosi: " + e.getMessage());
        }
    }

    private void showUserProfile(Long adminId, Long userId) {
        try {
            String sql = "SELECT * FROM users WHERE telegram_id = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setLong(1, userId);
                try (ResultSet rs = pstmt.executeQuery()) {

                    if (rs.next()) {
                        String profile = String.format(
                                "👤 *Foydalanuvchi profili:*\n\n" +
                                        "🆔 ID: %d\n" +
                                        "👤 Ism: %s\n" +
                                        "📱 Username: @%s\n" +
                                        "📞 Telefon: %s\n" +
                                        "💰 Balans: %,.0f so'm\n" +
                                        "👑 Admin: %s\n" +
                                        "📅 Ro'yxatdan o'tgan: %s",
                                rs.getLong("telegram_id"),
                                rs.getString("first_name"),
                                rs.getString("username"),
                                rs.getString("phone") != null ? rs.getString("phone") : "yo'q",
                                rs.getDouble("balance"),
                                rs.getBoolean("is_admin") ? "✅" : "❌",
                                rs.getTimestamp("created_at").toLocalDateTime()
                                        .format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm"))
                        );

                        sendMessage(adminId, profile);
                    } else {
                        sendMessage(adminId, "❌ Foydalanuvchi topilmadi!");
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Profil olish xatosi: " + e.getMessage());
        }
    }

    private void handleRejectionReason(Long adminId, String reason) {
        try {
            Long userId = Long.parseLong(tempData.get(adminId + "_reject_user"));
            Integer paymentId = Integer.parseInt(tempData.get(adminId + "_reject_payment"));

            // Payment ni rad etish
            String sql = "UPDATE payments SET status = 'rejected', admin_checked = true, rejection_reason = ? WHERE id = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, reason);
                pstmt.setInt(2, paymentId);
                pstmt.executeUpdate();
            }

            // Foydalanuvchiga xabar
            sendMessage(userId, "❌ To'lovingiz rad etildi!\n\n" +
                    "📝 *Sabab:* " + reason + "\n\n" +
                    "Agar bu xatolik deb o'ylasangiz, qayta urinib ko'ring yoki admin bilan bog'laning.");

            // Admin ga xabar
            sendMessage(adminId, "✅ To'lov rad etildi! Foydalanuvchiga sabab yuborildi.");

            // Tozalash
            userStates.remove(adminId);
            tempData.remove(adminId + "_reject_user");
            tempData.remove(adminId + "_reject_payment");

        } catch (SQLException e) {
            System.err.println("Rad etish xatosi: " + e.getMessage());
        }
    }

    private void sendMessage(Long chatId, String text) {
        try {
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText(text);
            message.setParseMode("Markdown");
            execute(message);
        } catch (TelegramApiException e) {
            System.err.println("Xabar yuborishda xatolik: " + e.getMessage());
        }
    }

    private void sendMessage(Long chatId, String text, InlineKeyboardMarkup markup) {
        try {
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText(text);
            message.setParseMode("Markdown");
            message.setReplyMarkup(markup);
            execute(message);
        } catch (TelegramApiException e) {
            System.err.println("Xabar yuborishda xatolik: " + e.getMessage());
        }
    }

    private void askFeedback(Long chatId) {
        sendMessage(chatId, "✍️ *Taklif yoki shikoyatingizni yozing:*\n\nBiz sizning fikringizni qayta ishlab, yaxshilashlar kiritamiz.");
        userStates.put(chatId, "WAITING_FEEDBACK");
    }

    @Override
    public String getBotUsername() {
        return BOT_USERNAME;
    }

    @Override
    public String getBotToken() {
        return BOT_TOKEN;
    }

    // ==================== INNER CLASSES ====================

    class Advert {
        private String fromRegion;
        private String fromAddress;
        private String toRegion;
        private String toAddress;
        private String productType;
        private String weight;
        private String phone;
        private String price;
        private String additionalInfo;
        private String status = "pending";
        private int postCount = 0;

        public String getFromRegion() { return fromRegion; }
        public void setFromRegion(String fromRegion) { this.fromRegion = fromRegion; }

        public String getFromAddress() { return fromAddress; }
        public void setFromAddress(String fromAddress) { this.fromAddress = fromAddress; }

        public String getToRegion() { return toRegion; }
        public void setToRegion(String toRegion) { this.toRegion = toRegion; }

        public String getToAddress() { return toAddress; }
        public void setToAddress(String toAddress) { this.toAddress = toAddress; }

        public String getProductType() { return productType; }
        public void setProductType(String productType) { this.productType = productType; }

        public String getWeight() { return weight; }
        public void setWeight(String weight) { this.weight = weight; }

        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }

        public String getPrice() { return price; }
        public void setPrice(String price) { this.price = price; }

        public String getAdditionalInfo() { return additionalInfo; }
        public void setAdditionalInfo(String additionalInfo) { this.additionalInfo = additionalInfo; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public int getPostCount() { return postCount; }
        public void setPostCount(int postCount) { this.postCount = postCount; }
    }

    class DriverAdvert {
        private String region;
        private String phone;
        private String carModel;
        private String carNumber;
        private String carCapacity;
        private String additionalInfo;
        private String status = "pending";
        private int postCount = 0;

        public String getRegion() { return region; }
        public void setRegion(String region) { this.region = region; }

        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }

        public String getCarModel() { return carModel; }
        public void setCarModel(String carModel) { this.carModel = carModel; }

        public String getCarNumber() { return carNumber; }
        public void setCarNumber(String carNumber) { this.carNumber = carNumber; }

        public String getCarCapacity() { return carCapacity; }
        public void setCarCapacity(String carCapacity) { this.carCapacity = carCapacity; }

        public String getAdditionalInfo() { return additionalInfo; }
        public void setAdditionalInfo(String additionalInfo) { this.additionalInfo = additionalInfo; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public int getPostCount() { return postCount; }
        public void setPostCount(int postCount) { this.postCount = postCount; }
    }

    class TelegramGroup {
        String groupId;
        String name;
        String link;
        int messageCount = 0;
    }
}