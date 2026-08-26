package com.priceradar.telegram.infrastructure;

import com.priceradar.telegram.application.TelegramBotCommand;
import com.priceradar.telegram.application.TelegramBotCommandRegistrar;
import com.priceradar.telegram.application.TelegramDeliveryException;
import com.priceradar.telegram.application.TelegramDeliveryFailureType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import java.util.List;

public final class TelegramCommandMenuInitializer implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(TelegramCommandMenuInitializer.class);

    private final TelegramBotCommandRegistrar commandRegistrar;

    public TelegramCommandMenuInitializer(TelegramBotCommandRegistrar commandRegistrar) {
        if (commandRegistrar == null) {
            throw new IllegalArgumentException("commandRegistrar must not be null");
        }
        this.commandRegistrar = commandRegistrar;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            commandRegistrar.registerCommands(List.of(TelegramBotCommand.values()));
        } catch (TelegramDeliveryException exception) {
            if (exception.getFailureType() == TelegramDeliveryFailureType.PERMANENT_FAILURE) {
                throw exception;
            }
            LOGGER.warn(
                    "Could not confirm Telegram command menu configuration, failureType={}, causeType={}, error={}",
                    exception.getFailureType(),
                    rootCauseType(exception),
                    exception.getMessage()
            );
        }
    }

    private String rootCauseType(Throwable exception) {
        Throwable rootCause = exception;
        while (rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
        }
        return rootCause.getClass().getName();
    }
}
