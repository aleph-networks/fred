/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package freenet.clients.http;

import java.io.IOException;
import java.net.URI;
import java.util.EnumMap;

import freenet.client.HighLevelSimpleClient;
import freenet.clients.http.wizardsteps.*;
import freenet.config.Config;
import freenet.l10n.NodeL10n;
import freenet.node.Node;
import freenet.node.NodeClientCore;
import freenet.support.LogThresholdCallback;
import freenet.support.Logger;
import freenet.support.Logger.LogLevel;
import freenet.support.api.HTTPRequest;

/**
 * A first time wizard aimed to ease the configuration of the node.
 *
 * TODO: a choose your CSS step?
 */
public class FirstTimeWizardToadlet extends Toadlet {
	private final NodeClientCore core;
	private final EnumMap<WIZARD_STEP, Step> steps;

        private static volatile boolean logMINOR;
	static {
		Logger.registerLogThresholdCallback(new LogThresholdCallback(){
			@Override
			public void shouldUpdate(){
				logMINOR = Logger.shouldLog(LogLevel.MINOR, this);
			}
		});
	}

	public enum WIZARD_STEP {
		WELCOME,
		// Before security levels, because once the network security level has been set, we won't redirect
		// the user to the wizard page.
		BROWSER_WARNING,
		// We have to set up UPnP before reaching the bandwidth stage, so we can autodetect bandwidth settings.
		MISC,
		OPENNET,
		SECURITY_NETWORK,
		SECURITY_PHYSICAL,
		NAME_SELECTION,
		BANDWIDTH,
		DATASTORE_SIZE,
		CONGRATZ
	}

	FirstTimeWizardToadlet(HighLevelSimpleClient client, Node node, NodeClientCore core) {
		//Generic Toadlet-related initialization.
		super(client);
		this.core = core;
		Config config = node.config;

		//Add GET handlers for steps.
		steps = new EnumMap<WIZARD_STEP, Step>(WIZARD_STEP.class);
		steps.put(WIZARD_STEP.WELCOME, new WELCOME(config));
		steps.put(WIZARD_STEP.BROWSER_WARNING, new BROWSER_WARNING());
		steps.put(WIZARD_STEP.MISC, new MISC(core, config));
		steps.put(WIZARD_STEP.OPENNET, new OPENNET());
		steps.put(WIZARD_STEP.SECURITY_NETWORK, new SECURITY_NETWORK(core, client));
		steps.put(WIZARD_STEP.SECURITY_PHYSICAL, new SECURITY_PHYSICAL(core, client));
		steps.put(WIZARD_STEP.NAME_SELECTION, new NAME_SELECTION(config));
		steps.put(WIZARD_STEP.BANDWIDTH, new BANDWIDTH(core, config));
		steps.put(WIZARD_STEP.DATASTORE_SIZE, new DATASTORE_SIZE(core, config));
		steps.put(WIZARD_STEP.CONGRATZ, new CONGRATZ(config));
	}

	public static final String TOADLET_URL = "/wizard/";

	public void handleMethodGET(URI uri, HTTPRequest request, ToadletContext ctx) throws ToadletContextClosedException, IOException {
		if(!ctx.isAllowedFullAccess()) {
			super.sendErrorPage(ctx, 403, "Unauthorized", NodeL10n.getBase().getString("Toadlet.unauthorized"));
			return;
		}

		//Read the current step from the URL parameter, defaulting to the welcome page if unset or invalid..
		WIZARD_STEP currentStep;
		try {
			currentStep = WIZARD_STEP.valueOf(request.getParam("step", WIZARD_STEP.WELCOME.toString()));
		} catch (IllegalArgumentException e) {
			currentStep = WIZARD_STEP.WELCOME;
		}
		//Skip the browser warning page if using Chrome because its incognito mode works from command line.
		if (currentStep == WIZARD_STEP.BROWSER_WARNING &&
			request.getHeader("user-agent").contains("Chrome")) {
			currentStep = WIZARD_STEP.MISC;
		} else if (currentStep == WIZARD_STEP.SECURITY_NETWORK && !request.isParameterSet("opennet")) {
			//If opennet isn't defined, re-ask.
			currentStep = WIZARD_STEP.OPENNET;
		}
		Step getStep = steps.get(currentStep);
		//Generate page to surround the content, using the step's title and without status or nav bars.
		PageNode pageNode = ctx.getPageMaker().getPageNode(NodeL10n.getBase().getString(
		        "FirstTimeWizardToadlet."+getStep.getTitleKey()), false, false, ctx);
		//Return the page to the browser.
		getStep.getStep(pageNode.content, request, ctx);
		writeHTMLReply(ctx, 200, "OK", pageNode.outer.generate());
	}

	/**
	 * @return whether wizard steps should log minor events.
	 */
	public static boolean shouldLogMinor() {
		return logMINOR;
	}

	public void handleMethodPOST(URI uri, HTTPRequest request, ToadletContext ctx) throws ToadletContextClosedException, IOException {

		if(!ctx.isAllowedFullAccess()) {
			super.sendErrorPage(ctx, 403, "Unauthorized", NodeL10n.getBase().getString("Toadlet.unauthorized"));
			return;
		}

		String passwd = request.getPartAsStringFailsafe("formPassword", 32);
		boolean noPassword = (passwd == null) || !passwd.equals(core.formPassword);
		if(noPassword) {
			if(logMINOR) Logger.minor(this, "No password ("+passwd+" should be "+core.formPassword+ ')');
			super.writeTemporaryRedirect(ctx, "invalid/unhandled data", "/");
			return;
		}

		try {
			//Attempt to parse the current step, defaulting to WELCOME if unspecified.
			String currentValue = request.getPartAsStringFailsafe("step", 20);
			WIZARD_STEP currentStep = currentValue.isEmpty() ? WIZARD_STEP.WELCOME : WIZARD_STEP.valueOf(currentValue);

			String redirectTo = steps.get(currentStep).postStep(request, ctx);
			if (redirectTo != null) {
				super.writeTemporaryRedirect(ctx, "Wizard redirecting.", redirectTo);
			}
		} catch (IllegalArgumentException e) {
			//Failed to parse enum value, redirect to start.
			//TODO: Should this be an error page instead? Suddenly redirecting to the beginning is confusing.
			super.writeTemporaryRedirect(ctx, "invalid/unhandled data", TOADLET_URL);
		}
	}

	@Override
	public String path() {
		return TOADLET_URL;
	}
}
