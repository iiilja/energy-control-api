INSERT INTO system_configuration (config_key, config_value, description)
VALUES ('sungrow.feed_in_limit.enabled', 'true',
        'Enable Sungrow feed-in limit scheduler: limits export to feed_in_limit.limit-kw when Nordpool price is below feed_in_limit.price-threshold-eur-mwh');
