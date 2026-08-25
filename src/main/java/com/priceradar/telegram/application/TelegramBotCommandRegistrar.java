package com.priceradar.telegram.application;

import java.util.List;

public interface TelegramBotCommandRegistrar {

    void registerCommands(List<TelegramBotCommand> commands);
}
