import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

type Registration = {
  full_name: string;
  email: string;
  password: string;
  company_id: string;
};

class HttpError extends Error {
  constructor(readonly status: number, message: string) {
    super(message);
  }
}

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
};

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...cors, "Content-Type": "application/json" },
  });
}

function parseRegistration(value: unknown): Registration {
  if (!value || typeof value !== "object") {
    throw new HttpError(400, "A valid JSON request body is required.");
  }
  const body = value as Record<string, unknown>;
  if (typeof body.full_name !== "string" || !body.full_name.trim()) {
    throw new HttpError(400, "Full name is required.");
  }
  if (body.full_name.trim().length > 200) throw new HttpError(400, "Full name is too long.");
  if (typeof body.email !== "string" || !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(body.email.trim())
    || body.email.trim().length > 254) {
    throw new HttpError(400, "Enter a valid email address.");
  }
  if (typeof body.password !== "string" || body.password.length < 8 || body.password.length > 128) {
    throw new HttpError(400, "Password must be between 8 and 128 characters.");
  }
  if (!/[A-Za-z]/.test(body.password) || !/[0-9]/.test(body.password)) {
    throw new HttpError(400, "Password must contain at least one letter and one number.");
  }
  if (typeof body.company_id !== "string"
    || !/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(body.company_id)) {
    throw new HttpError(400, "Choose an available company.");
  }
  return {
    full_name: body.full_name.trim(),
    email: body.email.trim().toLowerCase(),
    password: body.password,
    company_id: body.company_id,
  };
}

Deno.serve(async (request) => {
  if (request.method === "OPTIONS") return new Response("ok", { headers: cors });

  try {
    const url = Deno.env.get("SUPABASE_URL");
    const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
    if (!url || !serviceKey) throw new HttpError(500, "Server configuration is incomplete.");
    const service = createClient(url, serviceKey);

    if (request.method === "GET" && new URL(request.url).searchParams.get("mode") === "companies") {
      const { data, error } = await service.from("companies")
        .select("id,name")
        .eq("is_active", true)
        .order("name", { ascending: true });
      if (error) {
        console.error("Could not load active companies:", error);
        throw new HttpError(500, "Available companies could not be loaded.");
      }
      return json(data ?? []);
    }
    if (request.method !== "POST") return json({ error: "Method not allowed" }, 405);

    let registration: Registration;
    try {
      registration = parseRegistration(await request.json());
    } catch (error) {
      if (error instanceof HttpError) throw error;
      throw new HttpError(400, "A valid JSON request body is required.");
    }

    const { data: created, error: createError } = await service.auth.admin.createUser({
      email: registration.email,
      password: registration.password,
      email_confirm: true,
    });
    if (createError) {
      const createMessage = createError.message.toLowerCase();
      if (createError.status === 422 || createMessage.includes("already registered")
        || createMessage.includes("already exists") || createMessage.includes("duplicate")) {
        throw new HttpError(409, "An account with that email already exists.");
      }
      console.error("Failed to create registration auth user:", createError);
      throw new HttpError(400, "Supabase Auth could not create the account.");
    }
    if (!created.user) throw new HttpError(500, "Supabase Auth did not return the created account.");

    const { error: profileError } = await service.rpc("register_account_profile", {
      p_id: created.user.id,
      p_full_name: registration.full_name,
      p_email: registration.email,
      p_company_id: registration.company_id,
    });
    if (profileError) {
      console.error("Failed to create pending account profile:", profileError);
      const { error: rollbackError } = await service.auth.admin.deleteUser(created.user.id);
      if (rollbackError) {
        console.error("Failed to roll back registration auth user:", rollbackError);
        throw new HttpError(500, "Registration could not be completed and account cleanup failed. Contact an administrator.");
      }
      if (profileError.code === "23505" || /duplicate key/i.test(profileError.message)) {
        throw new HttpError(409, "An account with that email already exists.");
      }
      throw new HttpError(500, "Could not save your registration. Please try again.");
    }

    return json({
      message: "Your registration has been submitted successfully. Please wait for an administrator to approve your account.",
    }, 201);
  } catch (error) {
    if (error instanceof HttpError) return json({ error: error.message }, error.status);
    console.error("Unhandled register-account error:", error);
    return json({ error: "Registration could not be submitted due to a server error." }, 500);
  }
});
