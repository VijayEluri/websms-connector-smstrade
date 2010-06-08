/*
 * Copyright (C) 2010
 * 
 * This file is part of WebSMS.
 * 
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program; If not, see <http://www.gnu.org/licenses/>.
 */
package de.ub0r.android.websms.connector.smstrade;

import java.net.HttpURLConnection;
import java.net.URLEncoder;

import org.apache.http.HttpResponse;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import de.ub0r.android.websms.connector.common.Connector;
import de.ub0r.android.websms.connector.common.ConnectorCommand;
import de.ub0r.android.websms.connector.common.ConnectorSpec;
import de.ub0r.android.websms.connector.common.Log;
import de.ub0r.android.websms.connector.common.Utils;
import de.ub0r.android.websms.connector.common.WebSMSException;
import de.ub0r.android.websms.connector.common.ConnectorSpec.SubConnectorSpec;

/**
 * AsyncTask to manage IO to smstrade.de API.
 * 
 * @author flx
 */
public class ConnectorSMStrade extends Connector {
	/** Tag for output. */
	private static final String TAG = "trade";
	/** {@link SubConnectorSpec} ID: basic. */
	private static final String ID_BASIC = "basic";
	/** {@link SubConnectorSpec} ID: economy. */
	private static final String ID_ECONOMY = "economy";
	/** {@link SubConnectorSpec} ID: gold. */
	private static final String ID_GOLD = "gold";
	/** {@link SubConnectorSpec} ID: direct. */
	private static final String ID_DIRECT = "direct";

	/** SMStrade Gateway URL. */
	private static final String URL = "https://gateway.smstrade.de/";

	/**
	 * {@inheritDoc}
	 */
	@Override
	public final ConnectorSpec initSpec(final Context context) {
		final String name = context.getString(R.string.connector_smstrade_name);
		ConnectorSpec c = new ConnectorSpec(name);
		c.setAuthor(// .
				context.getString(R.string.connector_smstrade_author));
		c.setBalance(null);
		c.setCapabilities(ConnectorSpec.CAPABILITIES_UPDATE
				| ConnectorSpec.CAPABILITIES_SEND
				| ConnectorSpec.CAPABILITIES_PREFS);
		c.addSubConnector(ID_BASIC, context.getString(R.string.basic), 0);
		c.addSubConnector(ID_ECONOMY, context.getString(R.string.economy), 0);
		c.addSubConnector(ID_GOLD, context.getString(R.string.gold), 0);
		c.addSubConnector(ID_DIRECT, context.getString(R.string.direct), 0);
		return c;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public final ConnectorSpec updateSpec(final Context context,
			final ConnectorSpec connectorSpec) {
		final SharedPreferences p = PreferenceManager
				.getDefaultSharedPreferences(context);
		if (p.getBoolean(Preferences.PREFS_ENABLED, false)) {
			if (p.getString(Preferences.PREFS_PASSWORD, "").length() > 0) {
				connectorSpec.setReady();
			} else {
				connectorSpec.setStatus(ConnectorSpec.STATUS_ENABLED);
			}
		} else {
			connectorSpec.setStatus(ConnectorSpec.STATUS_INACTIVE);
		}
		return connectorSpec;
	}

	/**
	 * Check return code from smstrade.com.
	 * 
	 * @param context
	 *            {@link Context}
	 * @param ret
	 *            return code
	 * @return true if no error code
	 * @throws WebSMSException
	 *             WebSMSException
	 */
	private static boolean checkReturnCode(final Context context, final int ret)
			throws WebSMSException {
		Log.d(TAG, "ret=" + ret);
		switch (ret) {
		case 100:
			return true;
		case 10:
			throw new WebSMSException(context, R.string.error_trade_10);
		case 20:
			throw new WebSMSException(context, R.string.error_trade_20);
		case 30:
			throw new WebSMSException(context, R.string.error_trade_30);
		case 31:
			throw new WebSMSException(context, R.string.error_trade_31);
		case 40:
			throw new WebSMSException(context, R.string.error_trade_40);
		case 50:
			throw new WebSMSException(context, R.string.error_trade_50);
		case 60:
			throw new WebSMSException(context, R.string.error_trade_60);
		case 70:
			throw new WebSMSException(context, R.string.error_trade_70);
		case 71:
			throw new WebSMSException(context, R.string.error_trade_71);
		case 80:
			throw new WebSMSException(context, R.string.error_trade_80);
		case 90:
			throw new WebSMSException(context, R.string.error_trade_90);
		default:
			throw new WebSMSException(context, R.string.error, " code: " + ret);
		}
	}

	/**
	 * Send data.
	 * 
	 * @param context
	 *            {@link Context}
	 * @param command
	 *            {@link ConnectorCommand}
	 * @throws WebSMSException
	 *             WebSMSException
	 */
	private void sendData(final Context context, final ConnectorCommand command)
			throws WebSMSException {
		// do IO
		try { // get Connection
			final StringBuilder url = new StringBuilder(URL);
			final ConnectorSpec cs = this.getSpec(context);
			final SharedPreferences p = PreferenceManager
					.getDefaultSharedPreferences(context);
			url.append("?user=");
			url.append(Utils.international2oldformat(Utils.getSender(context,
					command.getDefSender())));
			url.append("&password=");
			url.append(Utils.md5(p.getString(Preferences.PREFS_PASSWORD, "")));
			final String text = command.getText();
			if (text != null && text.length() > 0) {
				final String sub = command.getSelectedSubConnector();
				url.append("&route=");
				url.append(sub);
				url.append("&message=");
				url.append(URLEncoder.encode(text, "ISO-8859-15"));
				url.append("&to=");
				url.append(Utils.joinRecipientsNumbers(Utils
						.national2international(command.getDefPrefix(), command
								.getRecipients()), ";", true));
			} else {
				url.append("credits/");
			}
			Log.d(TAG, "--HTTP GET--");
			Log.d(TAG, url.toString());
			Log.d(TAG, "--HTTP GET--");
			// send data
			HttpResponse response = Utils.getHttpClient(url.toString(), null,
					null, null, null);
			int resp = response.getStatusLine().getStatusCode();
			if (resp != HttpURLConnection.HTTP_OK) {
				throw new WebSMSException(context, R.string.error_http, ""
						+ resp);
			}
			String htmlText = Utils.stream2str(
					response.getEntity().getContent()).trim();
			if (htmlText == null || htmlText.length() == 0) {
				throw new WebSMSException(context, R.string.error_service);
			}
			Log.d(TAG, "--HTTP RESPONSE--");
			Log.d(TAG, htmlText);
			Log.d(TAG, "--HTTP RESPONSE--");
			String[] lines = htmlText.split("\n");
			htmlText = null;
			int l = lines.length;
			if (text != null && text.length() > 0) {
				try {
					final int ret = Integer.parseInt(lines[0].trim());
					checkReturnCode(context, ret);
					if (l > 1) {
						cs.setBalance(lines[l - 1].trim());
					}
				} catch (NumberFormatException e) {
					Log.e(TAG, "could not parse ret", e);
					throw new WebSMSException(e.getMessage());
				}
			} else {
				cs.setBalance(lines[l - 1].trim());
			}
		} catch (Exception e) {
			Log.e(TAG, null, e);
			throw new WebSMSException(e.getMessage());
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected final void doUpdate(final Context context, final Intent intent)
			throws WebSMSException {
		this.sendData(context, new ConnectorCommand(intent));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected final void doSend(final Context context, final Intent intent)
			throws WebSMSException {
		this.sendData(context, new ConnectorCommand(intent));
	}
}
