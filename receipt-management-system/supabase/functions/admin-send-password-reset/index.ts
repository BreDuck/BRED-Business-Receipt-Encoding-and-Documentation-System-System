import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

class HttpError extends Error {
  constructor(readonly status: number, message: string) {
    super(message);
  }
}

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

function json(body: Record<string, unknown>, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...cors, "Content-Type": "application/json" },
  });
}

function requiredEnv(name: string): string {
  const value = Deno.env.get(name);
  if (!value) throw new HttpError(500, `Server configuration is missing ${name}.`);
  return value;
}

function escapeHtml(value: string): string {
  const entities: Record<string, string> = {
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    "\"": "&quot;",
    "'": "&#39;",
  };
  return value.replace(/[&<>"']/g, (character) => entities[character] ?? character);
}

function resetEmailHtml(name: string, company: string, actionLink: string, logoUrl: string): string {
  const safeName = escapeHtml(name);
  const safeCompany = escapeHtml(company);
  const safeLink = escapeHtml(actionLink);
  const safeLogo = escapeHtml(logoUrl);
  return `<!doctype html>
<html lang="en">
  <body style="margin:0;padding:24px;background:#FFF9E8;font-family:Arial,Helvetica,sans-serif;color:#202124;">
    <table role="presentation" cellpadding="0" cellspacing="0" width="100%" style="max-width:600px;margin:0 auto;background:#FFFDF5;border:1px solid #EFE2B7;border-radius:16px;overflow:hidden;">
      <tr>
        <td style="padding:32px 28px 24px;text-align:center;border-bottom:4px solid #F7A72E;">
          <img src="${safeLogo}" width="88" height="88" alt="BreDuck mascot" style="display:block;margin:0 auto 12px;border-radius:50%;">
          <div style="font-size:30px;font-weight:800;letter-spacing:4px;color:#6F4000;">BRED</div>
          <div style="margin-top:8px;font-size:20px;font-weight:700;color:#202124;">${safeCompany}</div>
          <div style="margin-top:5px;font-size:13px;color:#747B87;">Business Receipt Encoding and Documentation System</div>
        </td>
      </tr>
      <tr>
        <td style="padding:30px 32px 24px;">
          <h1 style="margin:0 0 16px;font-size:22px;color:#202124;">Reset your password</h1>
          <p style="margin:0 0 16px;line-height:1.6;">Hello ${safeName},</p>
          <p style="margin:0 0 24px;line-height:1.6;">A password reset was requested for your BRED account. Use the button below to choose a new password. If you did not request this, you can ignore this email.</p>
          <p style="margin:0 0 24px;text-align:center;">
            <a href="${safeLink}" style="display:inline-block;padding:13px 24px;border-radius:8px;background:#F7A72E;color:#202124;font-weight:700;text-decoration:none;">Reset password</a>
          </p>
          <p style="margin:0 0 8px;font-size:12px;line-height:1.5;color:#747B87;">If the button does not work, copy and paste this link into your browser:</p>
          <p style="margin:0;word-break:break-all;font-size:12px;line-height:1.5;"><a href="${safeLink}" style="color:#6F4000;">${safeLink}</a></p>
        </td>
      </tr>
      <tr>
        <td style="padding:18px 28px;text-align:center;background:#FFF9E8;color:#6F4000;font-size:12px;line-height:1.5;">
          BreDuck's Signature · BRED · ${safeCompany}
        </td>
      </tr>
    </table>
  </body>
</html>`;
}

async function sendBrandedResetEmail(
  recipient: string,
  name: string,
  company: string,
  actionLink: string,
  resendApiKey: string,
  emailFrom: string,
): Promise<void> {
  const logoUrl = Deno.env.get("BRED_LOGO_URL")
    ?? "https://raw.githubusercontent.com/Clarkisbred/receipt-management-system/main/receipt-management-system/src/main/resources/com/bred/rms/ui/logo/breducklogo.png";
  let parsedLogoUrl: URL;
  try {
    parsedLogoUrl = new URL(logoUrl);
  } catch {
    throw new HttpError(500, "The BRED logo URL is invalid.");
  }
  if (parsedLogoUrl.protocol !== "https:") {
    throw new HttpError(500, "The BRED logo URL must use HTTPS.");
  }

  const response = await fetch("https://api.resend.com/emails", {
    method: "POST",
    headers: {
      "Authorization": `Bearer ${resendApiKey}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      from: emailFrom,
      to: [recipient],
      subject: "Reset your BRED password",
      html: resetEmailHtml(name, company, actionLink, parsedLogoUrl.toString()),
      text: `BRED\n${company}\nBusiness Receipt Encoding and Documentation System\n\n`
        + `Hello ${name},\n\nA password reset was requested for your BRED account. `
        + `Choose a new password using this link: ${actionLink}\n\n`
        + "If you did not request this, you can ignore this email.\n\nBreDuck's Signature",
    }),
    signal: AbortSignal.timeout(15_000),
  });
  if (!response.ok) {
    console.error("Resend rejected the password reset email with status:", response.status);
    throw new HttpError(502, "The branded password reset email could not be delivered.");
  }
}

Deno.serve(async (request) => {
  if (request.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (request.method !== "POST") return json({ error: "Method not allowed" }, 405);

  try {
    const body = await request.json().catch(() => null);
    if (!body || typeof body.user_id !== "string"
      || !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(body.user_id)) {
      throw new HttpError(400, "A valid user_id is required.");
    }

    const url = requiredEnv("SUPABASE_URL");
    const serviceKey = requiredEnv("SUPABASE_SERVICE_ROLE_KEY");
    const anonKey = requiredEnv("SUPABASE_ANON_KEY");
    const authorization = request.headers.get("Authorization");
    if (!authorization) throw new HttpError(401, "Authentication required.");

    const service = createClient(url, serviceKey);
    const caller = createClient(url, anonKey, {
      global: { headers: { Authorization: authorization } },
    });
    const { data: callerData, error: authError } = await caller.auth.getUser();
    if (authError || !callerData.user) throw new HttpError(401, "Authentication required.");

    const { data: actor, error: actorError } = await service.from("profiles")
      .select("role,is_active,account_status,full_name,company_id")
      .eq("id", callerData.user.id).maybeSingle();
    if (actorError) {
      console.error("Failed to verify Creator profile:", actorError);
      throw new HttpError(500, "Could not verify Creator permissions.");
    }
    if (!actor || actor.role !== "creator" || !actor.is_active || actor.account_status !== "APPROVED") {
      throw new HttpError(403, "An approved, active Creator account is required.");
    }

    const { data: target, error: targetError } = await service.from("profiles")
      .select("email,full_name,company_id")
      .eq("id", body.user_id).maybeSingle();
    if (targetError) {
      console.error("Failed to load account for password reset:", targetError);
      throw new HttpError(500, "Could not load the account.");
    }
    if (!target) throw new HttpError(404, "That account no longer exists.");
    const resendApiKey = requiredEnv("RESEND_API_KEY");
    const emailFrom = requiredEnv("EMAIL_FROM");

    let companyName = "Your company";
    if (target.company_id) {
      const { data: company, error: companyError } = await service.from("companies")
        .select("name").eq("id", target.company_id).maybeSingle();
      if (companyError) {
        console.error("Failed to load company for password reset email:", companyError);
        throw new HttpError(500, "Could not load the account's company.");
      }
      if (company?.name) companyName = company.name;
    }

    const { data: recoveryLink, error: linkError } = await service.auth.admin.generateLink({
      type: "recovery",
      email: target.email,
    });
    const actionLink = recoveryLink?.properties?.action_link;
    if (linkError || !actionLink) {
      console.error("Failed to generate password recovery link:", linkError);
      throw new HttpError(500, "A password reset link could not be generated.");
    }
    await sendBrandedResetEmail(target.email, target.full_name, companyName, actionLink, resendApiKey, emailFrom);

    const { error: activityError } = await service.from("activity_log").insert({
      user_id: callerData.user.id,
      user_name: actor.full_name,
      company_id: actor.company_id,
      action: "user.password_reset",
      detail: target.email,
    });
    if (activityError) {
      console.error("Failed to record password reset activity:", activityError);
    }
    return json({ sent: true });
  } catch (error) {
    if (error instanceof HttpError) return json({ error: error.message }, error.status);
    console.error("Unhandled admin-send-password-reset error:", error);
    return json({ error: "The password reset email could not be sent due to a server error." }, 500);
  }
});
