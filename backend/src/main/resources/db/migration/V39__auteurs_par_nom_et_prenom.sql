-- Les champs « cree par » et « modifie par » gardaient l'identifiant de connexion
-- (adresse e-mail). Les rapports affichent desormais le prenom et le nom : les
-- valeurs deja enregistrees sont converties ici, table par table.
DO $$
DECLARE
    colonne record;
BEGIN
    FOR colonne IN
        SELECT c.table_name, c.column_name
        FROM information_schema.columns c
        JOIN information_schema.tables t
          ON t.table_schema = c.table_schema AND t.table_name = c.table_name AND t.table_type = 'BASE TABLE'
        WHERE c.table_schema = current_schema()
          AND c.column_name IN ('cree_par', 'modifie_par')
    LOOP
        EXECUTE format(
            'UPDATE %I x SET %I = left(CASE WHEN u.prenom IS NULL OR btrim(u.prenom) = '''' THEN u.nom
                                            ELSE u.prenom || '' '' || u.nom END, 100)
             FROM utilisateur u
             WHERE lower(x.%I) = lower(u.email)',
            colonne.table_name, colonne.column_name, colonne.column_name);
    END LOOP;
END $$;
