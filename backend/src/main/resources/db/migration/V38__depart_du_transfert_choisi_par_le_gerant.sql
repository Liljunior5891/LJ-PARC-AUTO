-- Un magasinier demande de la marchandise pour son lieu sans voir le stock des autres :
-- c'est le gerant qui choisit le lieu de depart, a la validation.
ALTER TABLE pdv_transfert ALTER COLUMN source_id DROP NOT NULL;
