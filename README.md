# TP Philidor

Une application Android en Java pour jouer aux échecs contre Philidor, un petit modèle de langage qui a appris à jouer en lisant des millions de parties.

## 1. Créer son dépôt

Sur la page de ce dépôt : bouton **Use this template → Create a new repository**.

- Un dépôt par étudiant.
- Public ou privé. S'il est privé, invitez l'enseignant : **Settings → Collaborators → Add people**.

## 2. Le cloner dans Android Studio

1. **File → New → Project from Version Control**, puis collez l'adresse de **votre** dépôt.
2. Attendez la fin de la synchronisation Gradle.
3. Lancez l'app (bouton ▶) : l'écran « Hello World! » s'affiche.

## 3. Le kit

Deux classes sont déjà en place dans `app/src/main/java/fr/mines/philidor/moteur/`. Utilisez-les **sans les modifier** :

- `Partie.java` : les règles des échecs (coups légaux, roque, prise en passant, promotion, échec et mat, nulles, notation algébrique) ;
- `Philidor.java` : le modèle qui choisit un coup. Ses deux méthodes `charger` et `choisirCoup` prennent du temps : jamais sur le fil principal.

ONNX Runtime, la bibliothèque qui fait tourner le modèle, est déjà dans les dépendances.

Les tests du kit sont fournis :

- `app/src/test/.../PartieTest.java` : les règles, sans appareil ;
- `app/src/androidTest/.../PhilidorTest.java` : le modèle, sur un émulateur ou un téléphone (il faut le fichier du modèle, voir ci-dessous).

## 4. Le modèle

Téléchargez **`philidor51m.onnx`** (54 Mo) depuis la page **Releases** de ce dépôt modèle :

https://github.com/mines-android-2026/tp-philidor/releases/download/v1/philidor51m.onnx

(page des versions : https://github.com/mines-android-2026/tp-philidor/releases)

Placez-le dans `app/src/main/assets/`. Si le dossier n'existe pas : clic droit sur `app/src/main` → **New → Folder → Assets Folder**.

- L'app se lance sans lui : il ne sert qu'à partir de l'étape 4.
- Il n'est **jamais** commité : il est déjà dans le `.gitignore`.

## 5. Rendre son travail

- Committez et poussez à la fin de chaque partie du sujet.
- Quand une partie est terminée, posez son tag, par exemple pour la partie 3 :

```
git tag partie-3
git push --tags
```
