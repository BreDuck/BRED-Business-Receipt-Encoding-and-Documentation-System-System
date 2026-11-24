-- RMS 007: reserve a global-visibility role for the system Creator.
alter type user_role add value if not exists 'creator';
