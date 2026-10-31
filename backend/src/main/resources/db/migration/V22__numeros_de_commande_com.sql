-- =============================================================================
-- V22 : les commandes encaissees sont numerotees COM-annee-0001 au lieu de
-- TK-annee-0001. Les numeros existants sont renommes ; le compteur garde sa
-- valeur, pour que la suite reprenne sans doublon. Les motifs des mouvements
-- de stock qui citaient l'ancien numero suivent.
-- =============================================================================

UPDATE pdv_vente
SET numero = 'COM' || substring(numero FROM 3)
WHERE numero LIKE 'TK-%';

UPDATE compteur_numero c
SET prefixe = 'COM'
WHERE c.prefixe = 'TK'
  AND NOT EXISTS (SELECT 1 FROM compteur_numero d
                  WHERE d.entreprise_id = c.entreprise_id AND d.prefixe = 'COM' AND d.annee = c.annee);

UPDATE pdv_mouvement_stock
SET motif = replace(replace(motif, 'Ticket TK-', 'Commande COM-'), 'TK-', 'COM-')
WHERE motif LIKE '%TK-%';
