import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

type RequestBody = {
  email: string;
  password: string;
  full_name: string;
  role: "admin" | "encoder";
};

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

function parseRequestBody(value: unknown): RequestBody {
  if (!value || typeof value !== "object") {
    throw new HttpError(400, "A valid JSON request body is required.");
  }
  const body = value as Record<string, unknown>;
  if (typeof body.email !== "string" || !body.email.trim()
    || typeof body.password !== "string" || !body.password
    || typeof body.full_name !== "string" || !body.full_name.trim()) {
    throw new HttpError(400, "email, password, and full_name are required.");
  }
  if (body.password.length < 6) {
    throw new HttpError(400, "Password must be at least 6 characters.");
  }
  if (body.role !== "admin" && body.role !== "encoder") {
    throw new HttpError(400, "Invalid role.");
  }
  return {
    email: body.email,
    password: body.password,
    full_name: body.full_name,
    role: body.role,
  };
}

async function rollbackUser(
  deleteUser: (userId: string) => Promise<{ error: Error | null }>,
  userId: string,
): Promise<boolean> {
  const { error } = await deleteUser(userId);
  if (error) {
    console.error("Failed to roll back created auth user:", error);
    return false;
  }
  return true;
}

Deno.serve(async (request) => {
  if (request.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (request.method !== "POST") return json({ error: "Method not allowed" }, 405);

  try {
    let body: RequestBody;
    try {
      body = parseRequestBody(await request.json());
    } catch (error) {
      if (error instanceof HttpError) throw error;
      throw new HttpError(400, "A valid JSON request body is required.");
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

    const { data: admin, error: profileLookupError } = await service
      .from("profiles")
      .select("role,is_active,account_status,full_name")
      .eq("id", callerData.user.id)
      .maybeSingle();
    if (profileLookupError) {
      console.error("Failed to verify administrator profile:", profileLookupError);
      throw new HttpError(500, "Could not verify administrator permissions.");
    }
    if (!admin || admin.role !== "admin" || !admin.is_active || admin.account_status !== "APPROVED") {
      throw new HttpError(403, "An approved, active administrator account is required.");
    }

    const email = body.email.trim();
    const { data: created, error: createError } = await service.auth.admin.createUser({
      email,
      password: body.password,
      email_confirm: true,
    });
    if (createError) {
      const createMessage = createError.message.toLowerCase();
      if (createError.status === 422 || createMessage.includes("already registered")
        || createMessage.includes("already exists") || createMessage.includes("duplicate")) {
        throw new HttpError(409, "An account with that email already exists.");
      }
      console.error("Failed to create auth user:", createError);
      throw new HttpError(400, "Supabase Auth could not create the account.");
    }
    if (!created.user) throw new HttpError(500, "Supabase Auth did not return the created account.");
    const userId = created.user.id;

    const { error: profileError } = await service.from("profiles").insert({
      id: userId,
      email,
      full_name: body.full_name.trim(),
      role: body.role,
      is_active: true,
      account_status: "APPROVED",
    });
    if (profileError) {
      console.error("Failed to create user profile:", profileError);
      const rolledBack = await rollbackUser((id) => service.auth.admin.deleteUser(id), userId);
      throw new HttpError(500, rolledBack
        ? "Could not create the user profile; the new account was rolled back."
        : "Could not create the user profile, and account cleanup failed. Contact an administrator.");
    }

    const { error: activityError } = await service.from("activity_log").insert({
      user_id: callerData.user.id,
      user_name: admin.full_name,
      action: "user.create",
      detail: `${email} (${userId})`,
    });
    if (activityError) {
      console.error("Failed to record user creation activity:", activityError);
      const rolledBack = await rollbackUser((id) => service.auth.admin.deleteUser(id), userId);
      throw new HttpError(500, rolledBack
        ? "Could not record the user creation; the new account was rolled back."
        : "Could not record the user creation, and account cleanup failed. Contact an administrator.");
    }

    return json({ id: userId });
  } catch (error) {
    if (error instanceof HttpError) return json({ error: error.message }, error.status);
    console.error("Unhandled admin-create-user error:", error);
    return json({ error: "The user could not be created due to a server error." }, 500);
  }
});
