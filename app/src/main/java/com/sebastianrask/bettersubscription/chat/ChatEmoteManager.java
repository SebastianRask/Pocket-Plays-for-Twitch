package com.sebastianrask.bettersubscription.chat;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;
import android.util.Log;

import com.sebastianrask.bettersubscription.R;
import com.sebastianrask.bettersubscription.model.Badge;
import com.sebastianrask.bettersubscription.model.ChatBadge;
import com.sebastianrask.bettersubscription.model.ChatEmote;
import com.sebastianrask.bettersubscription.model.Emote;
import com.sebastianrask.bettersubscription.service.Service;
import com.sebastianrask.bettersubscription.service.Settings;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by sebastian on 26/07/2017.
 */

public class ChatEmoteManager {

    final String LOG_TAG = getClass().getSimpleName();

    private static LruCache<String, Bitmap> cachedEmotes = new LruCache<>(4 * 1024 * 1024);
    private static LruCache<String, ChatBadge> cachedBadges = new LruCache<>(4 * 1024 * 1024);

    private static Map<String, String> bttvEmotesToId;

    private static final int 	EMOTE_SMALL_SIZE 	= 20,
                                EMOTE_MEDIUM_SIZE 	= 30,
                                EMOTE_LARGE_SIZE 	= 40;

    private final List<Emote> bttvGlobal = new ArrayList<>();
    private final List<Emote> bttvChannel = new ArrayList<>();

    private static final Map<String, Badge> badgesGlobal = new HashMap();
    private static final Map<String, Badge> badgesChannel = new HashMap();

    private Pattern bttvEmotesPattern = Pattern.compile("");
    private Pattern emotePattern = Pattern.compile("(\\d+):((?:\\d+-\\d+,?)+)");

    private Settings settings;
    private Context context;
    private int channelId;
    private String channelName;

    public ChatEmoteManager(int channelId, String channelName, Context context) {
        this.context = context;
        this.channelId = channelId;
        this.channelName = channelName;
        this.settings = new Settings(context);
    }

    /**
     * Connects to the Better Twitch Tv API.
     * Fetches and maps the emote keywords and id's
     * This must not be called on main UI thread
     */
    protected void loadBttvEmotes(EmoteFetchCallback callback) {
        Map<String, String> result = new HashMap<>();
        String emotesPattern = "";

        final String BASE_GLOBAL_URL = "https://api.betterttv.net/2/emotes";
        final String BASE_CHANNEL_URL = "https://api.betterttv.net/2/channels/" + channelName;
        final String EMOTE_ARRAY = "emotes";
        final String EMOTE_ID = "id";
        final String EMOTE_WORD = "code";

        try {
            JSONObject topObject = new JSONObject(Service.urlToJSONString(BASE_GLOBAL_URL));
            JSONArray globalEmotes = topObject.getJSONArray(EMOTE_ARRAY);

            for (int i = 0; i < globalEmotes.length(); i++) {
                JSONArray arrayWithEmote = globalEmotes;

                JSONObject emoteObject = arrayWithEmote.getJSONObject(i);
                String emoteKeyword = emoteObject.getString(EMOTE_WORD);
                String emoteId = emoteObject.getString(EMOTE_ID);
                result.put(emoteKeyword, emoteId);

                Emote emote = new Emote(emoteId, emoteKeyword, true);
                bttvGlobal.add(emote);

                if (emotesPattern.equals("")) {
                    emotesPattern = Pattern.quote(emoteKeyword);
                } else {
                    emotesPattern += "|" + Pattern.quote(emoteKeyword);
                }
            }

            JSONObject topChannelEmotes = new JSONObject(Service.urlToJSONString(BASE_CHANNEL_URL));
            JSONArray channelEmotes = topChannelEmotes.getJSONArray(EMOTE_ARRAY);
            for (int i = 0; i < channelEmotes.length(); i++) {
                JSONArray arrayWithEmote = channelEmotes;

                JSONObject emoteObject = arrayWithEmote.getJSONObject(i);
                String emoteKeyword = emoteObject.getString(EMOTE_WORD);
                String emoteId = emoteObject.getString(EMOTE_ID);
                result.put(emoteKeyword, emoteId);

                Emote emote = new Emote(emoteId, emoteKeyword, true);
                emote.setBetterTTVChannelEmote(true);
                bttvChannel.add(emote);

                if (emotesPattern.equals("")) {
                    emotesPattern = Pattern.quote(emoteKeyword);
                } else {
                    emotesPattern += "|" + Pattern.quote(emoteKeyword);
                }
            }


        } catch (JSONException e) {
            e.printStackTrace();
        }

        bttvEmotesPattern = Pattern.compile("\\b(" + emotesPattern + ")\\b");
        bttvEmotesToId = result;

        try {
            callback.onEmoteFetched();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Fetches Twitch global chat badge keywords and their versions.
     * Must not be called from the main thread.
     */
    protected void loadGlobalChatBadges(EmoteFetchCallback callback) {
        loadBadgeSetsFromURL("https://badges.twitch.tv/v1/badges/global/display", badgesGlobal,
                callback);
    }

    /**
     * Fetches Twitch channel chat badge keywords and their versions.
     * Must not be called from the main thread.
     */
    protected void loadChannelChatBadges(EmoteFetchCallback callback) {
        // We need to remove the channel badges from the static cache, because
        // the names overlap between channels, e.g. "subscriber/0"
        for (Map.Entry<String, Badge> entry : badgesChannel.entrySet()) {
            for (String version : entry.getValue().getVersions()) {
                cachedBadges.remove(entry.getKey() + "/" + version);
            }
        }
        badgesChannel.clear();

        loadBadgeSetsFromURL("https://badges.twitch.tv/v1/badges/channels/" +channelId + "/display",
                badgesChannel, callback);
    }


    /**
     * Fetches Twitch global chat badge keywords and their versions.
     * Must not be called from the main thread.
     */
    private void loadBadgeSetsFromURL(String url, Map<String,Badge> dest, EmoteFetchCallback callback) {
        try {
            final JSONObject root = new JSONObject(Service.urlToJSONString(url))
                    .getJSONObject("badge_sets");

            for (final Iterator<String> iter = root.keys(); iter.hasNext();) {

                final String badgeName = iter.next();
                final JSONObject badgeJSON = root.getJSONObject(badgeName);
                if (badgeJSON == null) {
                    continue;
                }

                final Badge badge = new Badge(badgeName);
                final JSONObject versions = badgeJSON.getJSONObject("versions");
                if (versions == null) {
                    continue;
                }

                for (final Iterator<String> versionsIter = versions.keys(); versionsIter.hasNext();) {
                    final String versionName = versionsIter.next();
                    final JSONObject version = versions.getJSONObject(versionName);
                    badge.addVersion(versionName, version.getString("image_url_2x"));
                }

                dest.put(badgeName, badge);
            }
        } catch(Exception e) {
            Log.d(LOG_TAG, "Failed to fetch Twitch global chat badges", e);
        } finally {
            callback.onEmoteFetched();
        }
    }

    /**
     * Finds and creates Better Twitch Tv emotes in a message and returns them.
     * @param message The message to find emotes in
     * @return The List of emotes in the message
     */
    protected List<ChatEmote> findBttvEmotes(String message) {
        List<ChatEmote> emotes = new ArrayList<>();
        Matcher bttvEmoteMatcher = bttvEmotesPattern.matcher(message);

        while (bttvEmoteMatcher.find()) {
            String emoteKeyword = bttvEmoteMatcher.group();
            String emoteId = bttvEmotesToId.get(emoteKeyword);

            String[] positions = new String[] {bttvEmoteMatcher.start() + "-" + (bttvEmoteMatcher.end() - 1)};
            Bitmap emote = getEmoteFromId(emoteId, true);
            if (emote != null) {
                final ChatEmote chatEmote = new ChatEmote(positions, emote);
                emotes.add(chatEmote);
            }
        }

        return emotes;
    }

    /**
     * Finds and creates Twitch emotes in an unsplit irc line.
     * @param message The line to find emotes in
     * @return The list of emotes from the line
     */
    protected List<ChatEmote> findTwitchEmotes(String message) {
        List<ChatEmote> emotes = new ArrayList<>();
        Matcher emoteMatcher = emotePattern.matcher(message);

        while(emoteMatcher.find()) {
            String emoteId = emoteMatcher.group(1);
            String[] positions = emoteMatcher.group(2).split(",");
            emotes.add(new ChatEmote(positions, getEmoteFromId(emoteId, false)));
        }

        return emotes;
    }

    protected List<ChatBadge> getChatBadgesForTag(String badgeTag) {
        List<ChatBadge> badges = new ArrayList<>();

        if (badgeTag == null || badgeTag.length() == 0) {
            return badges;
        }

        for (final String item : badgeTag.split(",")) {
            if (item.length() == 0) {
                continue;
            }

            // Do we already have this in memory?
            ChatBadge chatBadge = cachedBadges.get(item);
            if (chatBadge != null) {
                badges.add(chatBadge);
                continue;
            }

            // Not in cache, go chase it down
            final String[] badgeSpec = item.split("/");
            if (badgeSpec.length != 2) {
                continue;
            }

            String url = null;

            // Check in channel badges first
            Badge badge = badgesChannel.get(badgeSpec[0]);
            if (badge != null) {
                url = badge.getVersion(badgeSpec[1]);
            }
            // Then fall back to global badges
            if (url == null) {
                badge = badgesGlobal.get(badgeSpec[0]);
                if (badge != null) {
                    url = badge.getVersion(badgeSpec[1]);
                }
            }

            if (url == null) {
                continue;
            }

            try {
                // We need to download the 2x image
                final Bitmap bmp = Service.getBitmapFromUrl(url);
                chatBadge = new ChatBadge(bmp);

                // In-memory cache
                cachedBadges.put(item, chatBadge);

                // TODO: use filesystem emote storage for further caching

                badges.add(chatBadge);
            } catch(Exception e) {
                Log.d(LOG_TAG, "Failed to fetch badge " + url, e);
            }
        }

        return badges;
    }

    /**
     * Returns a Bitmap of the emote with the specified emote id.
     * If the emote has not been cached from an earlier download the method
     * connects to the twitchemotes.com API to get the emote image.
     * The image is cached and converted to a bitmap which is returned.
     */
    protected Bitmap getEmoteFromId(String emoteId, boolean isBttvEmote) {
        String emoteKey = getEmoteStorageKey(emoteId, settings.getEmoteSize());
        if(cachedEmotes.get(emoteKey) != null) {
            return cachedEmotes.get(emoteKey);
        } else if (Service.doesStorageFileExist(emoteKey, context) && settings.getSaveEmotes()) {
            try {
                Bitmap emote = Service.getImageFromStorage(emoteKey, context);
                if (emote != null) {
                    cachedEmotes.put(emoteKey, emote);
                }
                return emote;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return saveAndGetEmote(getEmoteFromInternet(isBttvEmote, emoteId), emoteId);
    }

    /**
     * Gets and loads an emote from the internet.
     * @param isBttvEmote Is the emote a Better Twitch Tv emote?
     * @param emoteId The id of the emote
     * @return The emote. Might be null.S
     */
    private Bitmap getEmoteFromInternet(boolean isBttvEmote, String emoteId) {
        int settingsSize = getApiEmoteSizeFromSettingsSize(settings.getEmoteSize());
        String emoteUrl = getEmoteUrl(isBttvEmote, emoteId, settingsSize);

        Bitmap emote = Service.getBitmapFromUrl(emoteUrl);
        emote = getDpSizedEmote(emote);

        return emote;
    }

    protected Bitmap getDpSizedEmote(Bitmap emote) {
        int settingsSize = settings.getEmoteSize();
        int dpSize;

        switch (settingsSize) {
            case 1:
                dpSize = EMOTE_SMALL_SIZE;
                break;
            case 2:
                dpSize = EMOTE_MEDIUM_SIZE;
                break;
            case 3:
                dpSize = EMOTE_LARGE_SIZE;
                break;
            default:
                dpSize = EMOTE_MEDIUM_SIZE;
        }

        if (emote == null) {
            return null;
        }

        return Service.getResizedBitmap(emote, dpSize, context);
    }

    private int getApiEmoteSizeFromSettingsSize(int settingsSize) {
        return settingsSize == 1 ? 2 : settingsSize;
    }

    /**
     * If emote is null an error emote is returned. Else the emote is saved to storage and cache, then returned.
     * @param emote The emote bitmap.
     * @param emoteId The id of the emote
     * @return The final emote image.
     */
    private Bitmap saveAndGetEmote(Bitmap emote, String emoteId) {
        if(emote != null) {
            String emoteKey = getEmoteStorageKey(emoteId, settings.getEmoteSize());
            if (settings.getSaveEmotes() && !Service.doesStorageFileExist(emoteKey, context)) {
                Service.saveImageToStorage(emote, emoteKey, context);
            }
            cachedEmotes.put(emoteKey, emote);
            return emote;
        } else {
            return BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_missing_emote);
        }
    }


    public List<Emote> getGlobalBttvEmotes() {
        return bttvGlobal;
    }

    public List<Emote> getChanncelBttvEmotes() {
        return bttvChannel;
    }

    public interface EmoteFetchCallback {
        void onEmoteFetched();
    }

    /**
     * Generates a key to save and map an emote bitmap in storage and cache.
     * @param emoteId The emotes id
     * @param size The emotes size
     * @return The key
     */
    public static String getEmoteStorageKey(String emoteId, int size) {
        return "emote-" + emoteId + "-" + size + "Upgraded";
    }

    public static String getEmoteUrl(boolean isEmoteBttv, String emoteId, int size) {
        return isEmoteBttv
                ? "https://cdn.betterttv.net/emote/" + emoteId + "/" + size + "x"
                : "https://static-cdn.jtvnw.net/emoticons/v1/" + emoteId + "/" + size + ".0";
    }

    public static String getEmoteUrl(Emote emote, int size) {
        return getEmoteUrl(emote.isBetterTTVEmote(), emote.getEmoteId(), size);
    }

    public static Bitmap constructMediumSizedEmote(Bitmap emote, Context context) {
        return Service.getResizedBitmap(emote, EMOTE_MEDIUM_SIZE, context);
    }
}
