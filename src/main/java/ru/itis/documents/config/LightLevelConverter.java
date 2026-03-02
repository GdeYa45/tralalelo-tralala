package ru.itis.documents.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;
import ru.itis.documents.domain.enums.LightLevel;

/**
 * Этап 10.1 (P0): свой Converter String -> enum.
 * Нужен, чтобы принимать значения из формы типа "полутень", "тень", "яркий свет".
 */
@Component
public class LightLevelConverter implements Converter<String, LightLevel> {

    @Override
    public LightLevel convert(String source) {
        return LightLevel.from(source);
    }
}