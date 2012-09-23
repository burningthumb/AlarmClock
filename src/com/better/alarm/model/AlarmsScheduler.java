/*
 * Copyright (C) 2012 Yuriy Kulikov yuriy.kulikov.87@gmail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.better.alarm.model;

import java.util.Calendar;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.PriorityQueue;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

class AlarmsScheduler implements IAlarmsScheduler {
    private static final String TAG = "AlarmsScheduler";
    private static final boolean DBG = true;
    static final String ACTION_FIRED = "com.better.alarm.ACTION_FIRED";
    static final String EXTRA_ID = "intent.extra.alarm";
    static final String EXTRA_TYPE = "intent.extra.type";

    private class ScheduledAlarm implements Comparable<ScheduledAlarm> {
        public final int id;
        public final Calendar calendar;
        public final CalendarType type;

        public ScheduledAlarm(int id, Calendar calendar, CalendarType type) {
            this.id = id;
            this.calendar = calendar;
            this.type = type;
        }

        @Override
        public boolean equals(Object o) {
            if (((ScheduledAlarm) o).id == id && ((ScheduledAlarm) o).type == type) {
                return true;
            } else {
                return false;
            }
        }

        @Override
        public int compareTo(ScheduledAlarm another) {
            return this.calendar.compareTo(another.calendar);
        }

    }

    private final Context mContext;

    private final PriorityQueue<ScheduledAlarm> queue;

    AlarmsScheduler(Context context) {
        mContext = context;
        queue = new PriorityQueue<ScheduledAlarm>();
    }

    @Override
    public void removeAlarm(int id) {
        ScheduledAlarm previousHead = queue.peek();
        for (Iterator<ScheduledAlarm> iterator = queue.iterator(); iterator.hasNext();) {
            ScheduledAlarm scheduledAlarm = (ScheduledAlarm) iterator.next();

            if (scheduledAlarm.id == id) {
                Log.d(TAG, "removing a ScheduledAlarm " + id + " type = " + scheduledAlarm.type.toString());
                iterator.remove();
            }
        }
        ScheduledAlarm currentHead = queue.peek();
        if (previousHead != currentHead) {
            setNextRTCAlert();
        }
    }

    private void setNextRTCAlert() {
        if (!queue.isEmpty()) {
            // TODO problems happen because we remove, we have to keep. Remove
            // only when it is in the past
            // or removed by someone
            ScheduledAlarm scheduledAlarm = queue.peek();
            setUpRTCAlarm(scheduledAlarm.id, scheduledAlarm.calendar, scheduledAlarm.type);
            Intent intent = new Intent(Intents.ACTION_ALARM_SCHEDULED);
            intent.putExtra(Intents.EXTRA_ID, scheduledAlarm.id);
            // TODO add type to the intent
            mContext.sendBroadcast(intent);
        } else {
            removeRTCAlarm();
            Intent intent = new Intent(Intents.ACTION_ALARMS_UNSCHEDULED);
            mContext.sendBroadcast(intent);
        }
    }

    private void removeRTCAlarm() {
        AlarmManager am = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
        PendingIntent sender = PendingIntent.getBroadcast(mContext, 0, new Intent(ACTION_FIRED),
                PendingIntent.FLAG_CANCEL_CURRENT);
        am.cancel(sender);
    }

    private void setUpRTCAlarm(int id, Calendar calendar, CalendarType type) {
        AlarmManager am = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
        if (DBG) Log.d(TAG, "Set alarm " + id + " on " + calendar.getTime().toLocaleString());

        if (DBG && calendar.before(Calendar.getInstance())) {
            throw new RuntimeException("Attempt to schedule alarm in the past: " + calendar.getTime().toLocaleString());
        }

        Intent intent = new Intent(ACTION_FIRED);
        intent.putExtra(EXTRA_ID, id);
        intent.putExtra(EXTRA_TYPE, type.name());
        PendingIntent sender = PendingIntent.getBroadcast(mContext, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
        am.set(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), sender);
    }

    @Override
    public void setAlarm(int id, Map<CalendarType, Calendar> activeCalendars) {
        ScheduledAlarm previousHead = queue.peek();
        for (Iterator<ScheduledAlarm> iterator = queue.iterator(); iterator.hasNext();) {
            if (iterator.next().id == id) {
                iterator.remove();
            }
        }

        for (Entry<CalendarType, Calendar> entry : activeCalendars.entrySet()) {
            ScheduledAlarm scheduledAlarm = new ScheduledAlarm(id, entry.getValue(), entry.getKey());

            if (queue.contains(scheduledAlarm)) {
                queue.remove(scheduledAlarm);
            }
            queue.add(scheduledAlarm);
        }
        ScheduledAlarm currentHead = queue.peek();
        if (previousHead != currentHead) {
            setNextRTCAlert();
        }
    }
}