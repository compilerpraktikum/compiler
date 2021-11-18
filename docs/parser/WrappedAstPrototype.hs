{-# LANGUAGE ExplicitForAll #-}
{-# LANGUAGE LambdaCase #-}
{-# LANGUAGE RankNTypes #-}
{-# LANGUAGE InstanceSigs #-}
{-# LANGUAGE TypeApplications #-}

data Stmt f g
  = IfStmt (f (Expr f)) (g (Stmt f g))
  | Return (f (Expr f))

data Type f = IntType | ArrayType (f (Type f))

data Parameter f = Parameter String (f (Type f))

data Member expr stmt other  = Method 
  (other (Type other)) 
  [other (Parameter other)]

data Expr f
  = Literal Int
  | Unary String (f (Expr f))

class Show1 f where
  show1 :: Show a => f a -> String

instance (Show1 f) => Show (Expr f) where
  show = \case
    Literal a -> "(Literal " ++ show a ++ ")"
    Unary s c -> "(Unary " ++ show s ++ " " ++ show1 c ++ ")"

map' :: (f (Expr f) -> g (Expr g)) -> Expr f -> Expr g
map' alg = \case
  Literal a -> Literal a
  Unary s v -> Unary s (alg v)

newtype ConstValue a = ConstValue {unValue :: Int}

exec :: Identity (Expr Identity) -> ConstValue (Expr ConstValue)
exec = \case
  Identity (Literal a) -> ConstValue a
  Identity (Unary s v) -> ConstValue (unValue $ exec v)

class Functor1 t where
  map1 :: forall g f. Functor g => (forall a. f a -> g a) -> t f -> t g

instance Functor1 Expr where
  map1 :: forall f g. Functor g => (forall a. f a -> g a) -> Expr f -> Expr g
  map1 f = \case
    Literal i -> Literal i
    Unary s c ->
      Unary s $ fmap (map1 @Expr f) (f c)

mapRec :: (f (Expr f) -> g (Expr g)) -> Expr f -> Expr g
mapRec f = \case
  Literal i -> Literal i
  Unary s c -> Unary s $ f c

mapRecConvertLenientToIdentity :: Expr Lenient -> Expr Identity
mapRecConvertLenientToIdentity =  mapRec (\(Valid s) -> Identity $ mapRecConvertLenientToIdentity s)

trymapRec :: Expr Identity
trymapRec = mapRecConvertLenientToIdentity validLenientAST

normalToLenientValid :: Expr Identity -> Expr Lenient
normalToLenientValid = map1 $ \(Identity a) -> Valid a

newtype Identity a = Identity a deriving (Show)

instance Show1 Identity where show1 a = show a

normalAST :: Expr Identity
normalAST = Unary "!" (Identity (Literal 2))

normalAstAsValid :: Expr Lenient
normalAstAsValid = normalToLenientValid normalAST

data Lenient a = Error String | Valid a deriving (Show)

validLenientAST :: Expr Lenient
validLenientAST = Unary "!" $ Valid $ Unary "^" $ Valid $ Literal 2

lenientAST :: Expr Lenient
lenientAST = Unary "!" (Valid (Unary "^" (Error "invalid")))

lenientStmt :: Stmt Lenient Lenient
lenientStmt = IfStmt (Valid lenientAST) (Valid (Return (Valid lenientAST)))

instance Functor Lenient where
  fmap f = \case
    Error s -> Error s
    Valid a -> Valid $ f a

lenientToMaybe :: Lenient a -> Maybe a
lenientToMaybe = \case
  Error _ -> Nothing
  Valid a -> Just a

lenientExprToMaybe :: Expr Lenient -> Expr Maybe
lenientExprToMaybe = map1 lenientToMaybe

validate :: Expr Lenient -> Maybe (Expr Identity)
validate = \case
    Literal a -> Just $ Literal a
    Unary op inner -> Unary op . Identity <$> (lenientToMaybe inner >>= validate)

data Ann a = Ann {ty :: String, content :: a}

instance Functor Ann where
  fmap f (Ann ty content) = Ann ty $ f content

newtype Compose f g a = Compose (f (g a))

exampleNested :: Compose Lenient Ann (Expr (Compose Lenient Ann))
exampleNested = Compose $ Valid $ Ann "Int" $ Unary "-" $ Compose $ Valid $ Ann "Int" $ Literal 2

data Positioned a = Positioned {pos :: Int, val :: a}

newtype Parsed a = Parsed ( Positioned (Ann a))

outOf :: Expr (Compose f g) -> Expr h
outOf = undefined 

asd :: Parsed (Expr Parsed)
asd = Parsed $ Positioned 2 $ Ann "int" $ Literal 2