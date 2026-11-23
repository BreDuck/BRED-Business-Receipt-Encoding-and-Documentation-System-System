package com.gudo.rms.repositories;

import com.gudo.rms.models.ActivityEntry;

import java.util.List;

/** Reads the activity log (admin only). Entries are written by the database itself. */
public interface ActivityLogRepository {
    /** Returns the newest entries first. */
    List<ActivityEntry> findAll();
}
