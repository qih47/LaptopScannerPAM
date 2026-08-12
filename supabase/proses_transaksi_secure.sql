-- ==============================================================================
-- SUPABASE RPC FUNCTION: proses_transaksi (SECURE & ATOMIC - WIB / ASIA/JAKARTA)
-- ==============================================================================
-- Fungsi ini menangani pencatatan transaksi IN/OUT secara atomik (ACID)
-- dengan jaminan zona waktu Waktu Indonesia Barat (WIB / Asia/Jakarta).
--
-- Cara pakai:
-- 1. Buka Supabase Dashboard > SQL Editor
-- 2. Salin seluruh script ini dan jalankan (Run)
-- ==============================================================================

-- 1. Hapus semua versi fungsi proses_transaksi lama agar tidak ada konflik
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN 
        SELECT oid::regprocedure AS func_signature 
        FROM pg_proc 
        WHERE proname = 'proses_transaksi' AND pronamespace = 'public'::regnamespace
    LOOP
        EXECUTE 'DROP FUNCTION IF EXISTS ' || r.func_signature || ' CASCADE;';
    END LOOP;
END $$;

-- 2. Buat fungsi proses_transaksi baru (Kunci WIB / Asia/Jakarta Presisi)
CREATE OR REPLACE FUNCTION public.proses_transaksi(
    p_status_io TEXT,
    p_laptop_uuid UUID,
    p_petugas_npp TEXT,
    p_keterangan TEXT,
    p_lokasi TEXT,
    p_perangkat_details JSONB
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_log_id UUID;
    v_item JSONB;
    v_no_seri TEXT;
    v_user_version INT;
    -- KUNCI ZONA WAKTU JAKARTA (WIB) DI SINI
    v_created_at TIMESTAMPTZ := (NOW() AT TIME ZONE 'Asia/Jakarta');
    v_result JSONB;
BEGIN
    -- 0. [GUARD APP VERSION] Cek versi aplikasi petugas (blokir <= 7)
    SELECT COALESCE(app_version_code, 0) INTO v_user_version
    FROM public.users
    WHERE npp = p_petugas_npp;

    IF v_user_version <= 7 THEN
        RAISE EXCEPTION 'Aplikasi versi lawas terdeteksi (v%). Wajib update ke versi terbaru untuk melakukan transaksi!', v_user_version;
    END IF;

    -- 1. INSERT KE TABEL UTAMA (monitoring_inout)
    INSERT INTO public.monitoring_inout (
        status_io,
        laptop_uuid,
        petugas_npp,
        lokasi,
        keterangan,
        created_at,
        "isOpen"
    ) VALUES (
        p_status_io,
        p_laptop_uuid,
        p_petugas_npp,
        p_lokasi,
        p_keterangan,
        v_created_at,
        0
    )
    RETURNING uuid INTO v_log_id;

    -- 2. INSERT KE TABEL DETAIL (monitoring_inout_detail) & UPDATE MASTER PERANGKAT
    IF p_perangkat_details IS NOT NULL AND jsonb_array_length(p_perangkat_details) > 0 THEN
        FOR v_item IN SELECT * FROM jsonb_array_elements(p_perangkat_details)
        LOOP
            v_no_seri := v_item->>'no_seri';

            -- Insert detail perangkat yang dibawa
            INSERT INTO public.monitoring_inout_detail (
                log_id,
                no_seri,
                merk,
                tipe,
                created_at
            ) VALUES (
                v_log_id,
                v_no_seri,
                v_item->>'merk',
                v_item->>'tipe',
                v_created_at
            );

            -- Update status terakhir perangkat pada tabel daftar_perangkat (SOT MUTLAK)
            UPDATE public.daftar_perangkat
            SET status_terakhir = p_status_io
            WHERE no_seri = v_no_seri;
        END LOOP;
    END IF;

    -- 3. KEMBALIKAN HASIL LOG UNTUK APLIKASI
    SELECT json_build_object(
        'uuid', v_log_id,
        'created_at', v_created_at,
        'status_io', p_status_io,
        'laptop_uuid', p_laptop_uuid,
        'petugas_npp', p_petugas_npp,
        'lokasi', p_lokasi,
        'keterangan', p_keterangan,
        'isOpen', 0
    )::JSONB INTO v_result;

    RETURN v_result;
EXCEPTION
    WHEN OTHERS THEN
        RAISE EXCEPTION 'proses_transaksi error: %', SQLERRM;
END;
$$;
