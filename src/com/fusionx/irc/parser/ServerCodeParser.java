/*
    HoloIRC - an IRC client for Android

    Copyright 2013 Lalit Maganti

    This file is part of HoloIRC.

    HoloIRC is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    HoloIRC is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with HoloIRC. If not, see <http://www.gnu.org/licenses/>.
 */

package com.fusionx.irc.parser;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import com.fusionx.common.Utils;
import com.fusionx.irc.Channel;
import com.fusionx.irc.Server;
import com.fusionx.irc.UserChannelInterface;
import com.fusionx.irc.enums.ServerEventType;
import com.fusionx.lightirc.R;
import com.fusionx.uiircinterface.MessageSender;

import java.util.ArrayList;

import static com.fusionx.common.Utils.isMotdAllowed;
import static com.fusionx.irc.constants.Constants.LOG_TAG;
import static com.fusionx.irc.constants.ServerReplyCodes.ERR_NICKNAMEINUSE;
import static com.fusionx.irc.constants.ServerReplyCodes.RPL_ENDOFMOTD;
import static com.fusionx.irc.constants.ServerReplyCodes.RPL_ENDOFWHO;
import static com.fusionx.irc.constants.ServerReplyCodes.RPL_MOTD;
import static com.fusionx.irc.constants.ServerReplyCodes.RPL_MOTDSTART;
import static com.fusionx.irc.constants.ServerReplyCodes.RPL_TOPIC;
import static com.fusionx.irc.constants.ServerReplyCodes.RPL_TOPICINFO;
import static com.fusionx.irc.constants.ServerReplyCodes.RPL_WHOREPLY;
import static com.fusionx.irc.constants.ServerReplyCodes.genericCodes;

public class ServerCodeParser {
    private final StringBuilder mStringBuilder = new StringBuilder();
    private final WhoParser mWhoParser;
    private final UserChannelInterface mUserChannelInterface;
    private final Context mContext;
    private final Server mServer;
    private final MessageSender mSender;

    ServerCodeParser(final Context context, final ServerLineParser parser) {
        mServer = parser.getServer();
        mUserChannelInterface = mServer.getUserChannelInterface();
        mWhoParser = new WhoParser(mUserChannelInterface);
        mContext = context;
        mSender = MessageSender.getSender(mServer.getTitle());
    }

    /**
     * The server is sending a code to us - parse what it is
     *
     * @param parsedArray - the array of the line (split by spaces)
     */
    void parseCode(final ArrayList<String> parsedArray) {
        final int code = Integer.parseInt(parsedArray.get(1));

        // Pretty common across all the codes
        Utils.removeFirstElementFromList(parsedArray, 3);
        final String message = parsedArray.get(0);

        switch (code) {
            case RPL_MOTDSTART:
                mStringBuilder.setLength(0);
                // Fall through here to RPL_MOTD case is intentional
            case RPL_MOTD:
                mStringBuilder.append(message.substring(1).trim()).append("\n");
                return;
            case RPL_TOPIC:
                parseTopicReply(parsedArray);
                return;
            case RPL_TOPICINFO:
                parseTopicInfo(parsedArray);
                return;
            case RPL_ENDOFMOTD:
                parseMOTDFinished(message);
                return;
            case RPL_WHOREPLY:
                mWhoParser.parseWhoReply(parsedArray);
                return;
            case RPL_ENDOFWHO:
                mWhoParser.parseWhoFinished();
                return;
            case ERR_NICKNAMEINUSE:
                final MessageSender sender = MessageSender.getSender(mServer.getTitle());
                final Bundle event = Utils.parcelDataForBroadcast(null,
                        ServerEventType.NickInUse, mContext.getString(R.string.parser_nick_in_use));
                sender.sendServerMessage(event);
                return;
            default:
                parseFallThroughCode(code, message);
        }
    }

    private void parseTopicReply(ArrayList<String> parsedArray) {
        final String channelName = parsedArray.get(0);
        final String topic = parsedArray.get(1);
        final Channel channel = mUserChannelInterface.getChannel(channelName);
        channel.setTopic(topic);
    }

    // TODO - maybe using a colorful nick here if available?
    // TODO - possible optimization - make a new parser for topic stuff
    // Allows reduced overhead of retrieving channel from interface
    private void parseTopicInfo(final ArrayList<String> parsedArray) {
        final String channelName = parsedArray.get(0);
        final String nick = Utils.getNickFromRaw(parsedArray.get(1));
        final Channel channel = mUserChannelInterface.getChannel(channelName);
        channel.setTopicSetter(nick);

        mSender.sendGenericChannelEvent(channel.getName(),
                String.format(mContext.getString(R.string.parser_new_topic),
                        channel.getTopic(), nick));
    }

    private void parseFallThroughCode(int code, String message) {
        if (genericCodes.contains(code)) {
            final Bundle event = Utils.parcelDataForBroadcast(null, ServerEventType.Generic, message);
            mSender.sendServerMessage(event);
        } else {
            // Not sure what to do here - TODO
            Log.v(LOG_TAG, message);
        }
    }

    /**
     * Packs up MOTD, adds it to the server details and sends a generic server event (if allowed)
     *
     * @param message - the final line of the MOTD command
     */
    private void parseMOTDFinished(String message) {
        mStringBuilder.append(message);

        final String MOTD = mStringBuilder.toString().trim();
        mServer.setMOTD(MOTD);

        if (isMotdAllowed(mContext)) {
            final Bundle event = Utils.parcelDataForBroadcast(null, ServerEventType.Generic, MOTD);
            mSender.sendServerMessage(event);
        }
    }
}