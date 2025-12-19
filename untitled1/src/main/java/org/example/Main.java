package org.example;

import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

public class Main {
    public static void main(String[] args) {
        System.out.println("🚀 YukBot ishga tushmoqda...");

        try {
            // Telegram bots API ni ishga tushirish
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);

            // Botni ro'yxatdan o'tkazish
            BotService bot = new BotService();
            botsApi.registerBot(bot);

            System.out.println("✅ Bot muvaffaqiyatli ishga tushirildi!");
            System.out.println("🤖 Bot username: " + bot.getBotUsername());
            System.out.println("📊 To'lov tizimi holati: " + (bot.getClass().getDeclaredField("paymentSystemEnabled") != null ?
                    "Mavjud" : "Mavjud emas"));

            // Botni doimiy ishlashini ta'minlash
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("🔴 Bot to'xtatilmoqda...");
                try {
                    if (bot.getClass().getDeclaredField("scheduler") != null) {
                        System.out.println("🔄 Scheduler to'xtatildi");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                System.out.println("👋 Bot to'xtatildi!");
            }));

        } catch (TelegramApiException e) {
            System.err.println("❌ Telegram API xatosi: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("❌ Umumiy xatolik: " + e.getMessage());
            e.printStackTrace();
        }
    }
}